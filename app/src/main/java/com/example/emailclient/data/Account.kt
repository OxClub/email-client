package com.example.emailclient.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A configured mail account. The password itself is NOT stored in this row —
 * it lives in EncryptedSharedPreferences (see CredentialStore). This table
 * only holds connection settings needed to reconnect.
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val emailAddress: String,
    val imapHost: String,
    val imapPort: Int = 993,
    val smtpHost: String,
    val smtpPort: Int = 587,
    val username: String, // usually same as emailAddress, but some providers differ
    val useSsl: Boolean = true
)

/** Well-known provider presets so users don't have to hunt for server settings. */
object ProviderPresets {
    data class Preset(
        val label: String,
        val imapHost: String,
        val imapPort: Int,
        val smtpHost: String,
        val smtpPort: Int
    )

    val presets = listOf(
        Preset("Gmail", "imap.gmail.com", 993, "smtp.gmail.com", 587),
        Preset("Outlook / Office 365", "outlook.office365.com", 993, "smtp.office365.com", 587),
        Preset("Yahoo Mail", "imap.mail.yahoo.com", 993, "smtp.mail.yahoo.com", 587),
        Preset("iCloud Mail", "imap.mail.me.com", 993, "smtp.mail.me.com", 587),
        Preset("Custom / Other", "", 993, "", 587)
    )
}
