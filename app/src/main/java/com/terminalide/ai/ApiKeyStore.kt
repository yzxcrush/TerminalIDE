package com.terminalide.ai

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 敏感信息（DeepSeek API Key）的加密存储，使用 EncryptedSharedPreferences：
 *   - MasterKey：AES256_GCM
 *   - PrefKeyEncryptionScheme：AES256_SIV
 *   - PrefValueEncryptionScheme：AES256_GCM
 *
 * 部分机型/系统（尤其国产 ROM 的 Keystore 兼容性问题）会在初始化时抛异常，
 * 这里做了降级处理：加密存储不可用时退回普通 SharedPreferences，避免启动闪退。
 */
@Suppress("DEPRECATION")
class ApiKeyStore(context: Context) {

    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "terminal_ide_secrets",
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (t: Throwable) {
        context.getSharedPreferences("terminal_ide_secrets_fallback", Context.MODE_PRIVATE)
    }

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
