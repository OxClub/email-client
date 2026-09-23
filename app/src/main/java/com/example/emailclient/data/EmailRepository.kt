package com.example.emailclient.data

import android.content.Context
import com.example.emailclient.network.MailService
import com.example.emailclient.oauth.GoogleAuthManager
import kotlinx.coroutines.flow.Flow

class EmailRepository(private val context: Context) {
    private val db = AppDatabase.get(context)
    private val credentials = CredentialStore(context)
    private val accountDao = db.accountDao()
    private val messageDao = db.messageDao()

    fun observeAccounts(): Flow<List<Account>> = accountDao.observeAll()

    fun observeFolder(accountId: Long, folder: String): Flow<List<EmailMessage>> =
        messageDao.observeFolder(accountId, folder)

    suspend fun addAccount(account: Account, password: String): Long {
        val id = accountDao.insert(account)
        credentials.savePassword(id, password)
        return id
    }

    /** Adds an account that authenticates via Google OAuth (no password stored). */
    suspend fun addGoogleOAuthAccount(account: Account, authStateJson: String): Long {
        val id = accountDao.insert(account)
        credentials.saveAuthState(id, authStateJson)
        return id
    }

    suspend fun removeAccount(account: Account) {
        credentials.clearPassword(account.id)
        credentials.clearAuthState(account.id)
        messageDao.clearAccount(account.id)
        accountDao.delete(account)
    }

    /**
     * Builds a MailService for this account, fetching a fresh OAuth access
     * token (and persisting the refreshed state) if the account uses Google
     * sign-in, or the stored password otherwise.
     */
    private suspend fun serviceFor(account: Account): MailService? {
        return if (account.authType == AuthType.GOOGLE_OAUTH) {
            val authStateJson = credentials.getAuthState(account.id) ?: return null
            val result = GoogleAuthManager.getFreshAccessToken(context, authStateJson)
            val (accessToken, updatedAuthStateJson) = result.getOrNull() ?: return null
            credentials.saveAuthState(account.id, updatedAuthStateJson) // persist refreshed token
            MailService(account, accessToken, isOAuth = true)
        } else {
            val pwd = credentials.getPassword(account.id) ?: return null
            MailService(account, pwd, isOAuth = false)
        }
    }

    suspend fun testLogin(account: Account, password: String): Result<Unit> =
        MailService(account, password, isOAuth = false).testConnection()

    /** Pulls any messages newer than what we already have cached, for one folder. */
    suspend fun syncFolder(account: Account, folder: String): Result<Int> {
        val service = serviceFor(account) ?: return Result.failure(Exception("No stored credentials"))
        val known = messageDao.getKnownUids(account.id, folder).toSet()
        val result = service.fetchNewHeaders(folder, known)
        return result.map { newMessages ->
            if (newMessages.isNotEmpty()) messageDao.insertAll(newMessages)
            newMessages.size
        }
    }

    suspend fun openMessage(account: Account, message: EmailMessage): Result<EmailMessage> {
        messageDao.setRead(message.localId, true)
        if (message.bodyFetched) return Result.success(message.copy(isRead = true))

        val service = serviceFor(account) ?: return Result.failure(Exception("No stored credentials"))
        val bodyResult = service.fetchFullBody(message.folder, message.uid)
        return bodyResult.map { (plain, html) ->
            val updated = message.copy(
                bodyPlainText = plain,
                bodyHtml = html,
                bodyFetched = true,
                isRead = true
            )
            messageDao.insert(updated)
            updated
        }
    }

    suspend fun toggleStar(message: EmailMessage) {
        messageDao.setStarred(message.localId, !message.isStarred)
    }

    suspend fun sendMessage(
        account: Account,
        to: List<String>,
        cc: List<String>,
        subject: String,
        body: String,
        inReplyToMessageId: String? = null
    ): Result<Unit> {
        val service = serviceFor(account) ?: return Result.failure(Exception("No stored credentials"))
        return service.send(to, cc, subject, body, inReplyToMessageId)
    }
}
