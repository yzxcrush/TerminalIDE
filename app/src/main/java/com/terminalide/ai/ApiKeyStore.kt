package com.terminalide.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 敏感信息（DeepSeek API Key）的加密存储，使用 EncryptedSharedPreferences：
 *   - MasterKey：AES256_GCM
 *   - PrefKeyEncryptionScheme：AES256_SIV
 *   - PrefValueEncryptionScheme：AES256_GCM
 */
@Suppress("DEPRECATION")
class ApiKeyStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "terminal_ide_secrets",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun saveKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    fun loadKey(): String? = prefs.getString(KEY_API_KEY, null)

    fun clearKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    companion object {
        private const val KEY_API_KEY = "deepseek_api_key"
    }
}
