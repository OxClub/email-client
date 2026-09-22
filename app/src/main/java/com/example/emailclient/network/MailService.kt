package com.example.emailclient.network

import com.example.emailclient.data.Account
import com.example.emailclient.data.EmailMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/**
 * Thin wrapper around JavaMail for connecting to a generic IMAP/SMTP account.
 * All calls are blocking JavaMail calls, so every entry point here switches
 * to Dispatchers.IO.
 *
 * Auth model: username + password (an "app password" for providers like
 * Gmail/Yahoo/iCloud that require one when 2FA is on, or a normal password
 * for providers that allow plain IMAP login). Full OAuth2 (e.g. "Sign in
 * with Google") is intentionally out of scope here — it requires registering
 * this app with the provider and handling a browser-based consent flow.
 */
class MailService(private val account: Account, private val password: String) {

    private fun imapSession(): Session {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", account.imapHost)
            put("mail.imaps.port", account.imapPort.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "15000")
        }
        return Session.getInstance(props)
    }

    private fun smtpSession(): Session {
        val props = Properties().apply {
            put("mail.smtp.host", account.smtpHost)
            put("mail.smtp.port", account.smtpPort.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.ssl.trust", account.smtpHost)
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "15000")
        }
        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() =
                PasswordAuthentication(account.username, password)
        })
    }

    /** Verifies the credentials/host settings work, without fetching anything. */
    suspend fun testConnection(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val store = imapSession().getStore("imaps")
            store.connect(account.imapHost, account.imapPort, account.username, password)
            store.close()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lists folder names available on the server (e.g. INBOX, Sent, Drafts). */
    suspend fun listFolders(): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val store = imapSession().getStore("imaps")
            store.connect(account.imapHost, account.imapPort, account.username, password)
            val names = store.defaultFolder.list("*").map { it.fullName }
            store.close()
            Result.success(names)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches headers + preview for the most recent [limit] messages in
     * [folderName] that aren't already in [knownUids], newest first.
     * Full bodies are NOT fetched here (kept fast for list view) — call
     * [fetchFullBody] when the user opens a message.
     */
    suspend fun fetchNewHeaders(
        folderName: String,
        knownUids: Set<Long>,
        limit: Int = 50
    ): Result<List<EmailMessage>> = withContext(Dispatchers.IO) {
        try {
            val store = imapSession().getStore("imaps")
            store.connect(account.imapHost, account.imapPort, account.username, password)
            val folder = store.getFolder(folderName)
            folder.open(Folder.READ_ONLY)
            val uidFolder = folder as? com.sun.mail.imap.IMAPFolder

            val total = folder.messageCount
            if (total == 0) {
                folder.close(false); store.close()
                return@withContext Result.success(emptyList())
            }
            val start = maxOf(1, total - (limit * 3)) // overfetch a bit to skip known ones
            val range = folder.getMessages(start, total)

            val results = mutableListOf<EmailMessage>()
            for (msg in range.reversed()) {
                val uid = uidFolder?.getUID(msg) ?: msg.messageNumber.toLong()
                if (uid in knownUids) continue
                val mime = msg as MimeMessage
                val from = (mime.from?.firstOrNull() as? InternetAddress)
                results.add(
                    EmailMessage(
                        accountId = account.id,
                        folder = folderName,
                        uid = uid,
                        messageId = mime.messageID ?: "",
                        subject = mime.subject ?: "(no subject)",
                        fromAddress = from?.address ?: "unknown",
                        fromName = from?.personal ?: from?.address ?: "Unknown",
                        toAddresses = mime.getRecipients(Message.RecipientType.TO)
                            ?.joinToString(", ") { it.toString() } ?: "",
                        dateEpochMillis = (mime.sentDate ?: mime.receivedDate)?.time ?: 0L,
                        bodyPreview = extractPreview(mime),
                        bodyPlainText = "",
                        bodyHtml = null,
                        isRead = mime.flags.contains(Flags.Flag.SEEN),
                        isStarred = mime.flags.contains(Flags.Flag.FLAGGED),
                        bodyFetched = false
                    )
                )
                if (results.size >= limit) break
            }
            folder.close(false)
            store.close()
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Downloads the full body (plain text + HTML if present) for one message by UID. */
    suspend fun fetchFullBody(folderName: String, uid: Long): Result<Pair<String, String?>> =
        withContext(Dispatchers.IO) {
            try {
                val store = imapSession().getStore("imaps")
                store.connect(account.imapHost, account.imapPort, account.username, password)
                val folder = store.getFolder(folderName) as com.sun.mail.imap.IMAPFolder
                folder.open(Folder.READ_WRITE) // READ_WRITE so opening marks it \Seen
                val msg = folder.getMessageByUID(uid)
                    ?: return@withContext Result.failure(Exception("Message not found"))
                val (plain, html) = extractBodies(msg as MimeMessage)
                folder.close(true)
                store.close()
                Result.success(plain to html)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Sends a new message. [inReplyToMessageId] is optional, for threading replies. */
    suspend fun send(
        to: List<String>,
        cc: List<String> = emptyList(),
        subject: String,
        body: String,
        inReplyToMessageId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val session = smtpSession()
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(account.emailAddress, account.displayName))
                setRecipients(Message.RecipientType.TO, to.map { InternetAddress(it) }.toTypedArray())
                if (cc.isNotEmpty()) {
                    setRecipients(Message.RecipientType.CC, cc.map { InternetAddress(it) }.toTypedArray())
                }
                setSubject(subject)
                setText(body)
                if (inReplyToMessageId != null) {
                    setHeader("In-Reply-To", inReplyToMessageId)
                    setHeader("References", inReplyToMessageId)
                }
            }
            Transport.send(message)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractPreview(mime: MimeMessage): String = try {
        val (plain, _) = extractBodies(mime)
        plain.take(160).replace(Regex("\\s+"), " ").trim()
    } catch (e: Exception) {
        ""
    }

    /** Walks a (possibly multipart) message and returns plain-text and HTML bodies. */
    private fun extractBodies(part: Part): Pair<String, String?> {
        var plain = ""
        var html: String? = null

        fun walk(p: Part) {
            when {
                p.isMimeType("text/plain") && plain.isEmpty() ->
                    plain = p.content as? String ?: ""
                p.isMimeType("text/html") && html == null ->
                    html = p.content as? String
                p.isMimeType("multipart/*") -> {
                    val mp = p.content as MimeMultipart
                    for (i in 0 until mp.count) walk(mp.getBodyPart(i))
                }
            }
        }
        walk(part)
        if (plain.isEmpty() && html != null) {
            plain = html!!.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
        }
        return plain to html
    }
}
