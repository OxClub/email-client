package com.example.emailclient.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores account passwords (or app passwords) encrypted-at-rest, keyed by
 * account id. Never put passwords in the Room database or in plain
 * SharedPreferences.
 */
class CredentialStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun savePassword(accountId: Long, password: String) {
        prefs.edit().putString("pwd_$accountId", password).apply()
    }

    fun getPassword(accountId: Long): String? = prefs.getString("pwd_$accountId", null)

    fun clearPassword(accountId: Long) {
        prefs.edit().remove("pwd_$accountId").apply()
    }
}
