package com.example.emailclient.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A cached copy of a message fetched over IMAP. `uid` is the IMAP UID, which
 * combined with the folder and account uniquely identifies the message on
 * the server, and lets us do incremental sync (only fetch UIDs we don't have).
 */
@Entity(tableName = "messages")
data class EmailMessage(
    val accountId: Long,
    val folder: String,
    val uid: Long,
    val messageId: String,        // RFC822 Message-ID header, used for threading/replies
    val subject: String,
    val fromAddress: String,
    val fromName: String,
    val toAddresses: String,      // comma-separated
    val dateEpochMillis: Long,
    val bodyPreview: String,      // short snippet for list view
    val bodyPlainText: String,    // full plain-text body (fetched lazily on open)
    val bodyHtml: String?,        // full HTML body, if available
    val isRead: Boolean = false,
    val isStarred: Boolean = false,
    val bodyFetched: Boolean = false, // true once full body has been downloaded
    @PrimaryKey(autoGenerate = false) val localId: Long = accountId * 10_000_000_000L + uid
)
