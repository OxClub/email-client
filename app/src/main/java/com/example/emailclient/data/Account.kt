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

    // Maps an email domain to the preset that serves it, so the app can go
    // straight from "email address" to correct IMAP/SMTP settings with no
    // provider picker or manual host entry needed.
    private val domainMap: Map<String, Preset> = mapOf(
        "gmail.com" to presets[0],
        "googlemail.com" to presets[0],
        "outlook.com" to presets[1],
        "hotmail.com" to presets[1],
        "live.com" to presets[1],
        "msn.com" to presets[1],
        "office365.com" to presets[1],
        "yahoo.com" to presets[2],
        "yahoo.co.uk" to presets[2],
        "ymail.com" to presets[2],
        "icloud.com" to presets[3],
        "me.com" to presets[3],
        "mac.com" to presets[3]
    )

    /**
     * Looks up server settings from an email address alone. Returns null for
     * domains we don't recognize — most other providers (company mail, ISP
     * mail, etc.) follow the same "imap.<domain>" / "smtp.<domain>"
     * convention, so callers can fall back to [guessFromDomain] instead of
     * asking the user to type hostnames.
     */
    fun forEmail(email: String): Preset? {
        val domain = email.substringAfter('@', "").lowercase().trim()
        return domainMap[domain]
    }

    /** Best-effort guess for domains not in [domainMap], using the common imap./smtp. convention. */
    fun guessFromDomain(email: String): Preset {
        val domain = email.substringAfter('@', "").lowercase().trim()
        return Preset(
            label = "Custom",
            imapHost = "imap.$domain",
            imapPort = 993,
            smtpHost = "smtp.$domain",
            smtpPort = 587
        )
    }
}

