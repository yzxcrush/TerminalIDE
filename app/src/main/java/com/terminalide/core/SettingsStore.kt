package com.terminalide.core

import android.content.Context
import android.content.SharedPreferences

/**
 * 非敏感设置：镜像源、AI 后端类型、端侧模型路径、API Base URL。
 * （敏感信息——DeepSeek API Key——见 ai/ApiKeyStore，走 EncryptedSharedPreferences。）
 */
class SettingsStore(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("terminal_ide_settings", Context.MODE_PRIVATE)

    /** 全局默认镜像源 id，见 packages/MirrorSource。 */
    var globalMirrorId: String
        get() = sp.getString(KEY_MIRROR, "official") ?: "official"
        set(v) = sp.edit().putString(KEY_MIRROR, v).apply()

    /** AI 后端：local（端侧小模型）| deepseek（DeepSeek 官方 API）。 */
    var aiBackendKind: String
        get() = sp.getString(KEY_AI_BACKEND, "local") ?: "local"
        set(v) = sp.edit().putString(KEY_AI_BACKEND, v).apply()

    /** 端侧小模型文件路径（运行期可选下载，不打包进 APK）。 */
    var localModelPath: String
        get() = sp.getString(KEY_MODEL_PATH, "") ?: ""
        set(v) = sp.edit().putString(KEY_MODEL_PATH, v).apply()

    /** DeepSeek API Base URL（可换镜像/兼容端点）。 */
    var apiBaseUrl: String
        get() = sp.getString(KEY_API_BASE, DEFAULT_API_BASE) ?: DEFAULT_API_BASE
        set(v) = sp.edit().putString(KEY_API_BASE, v).apply()

    /**
     * App 语言：system（跟随系统）/ zh（简体）/ en（English）/ zh-TW（繁體）/ ja（日本語）。
     */
    var appLanguage: String
        get() = sp.getString(KEY_LANGUAGE, "system") ?: "system"
        set(v) = sp.edit().putString(KEY_LANGUAGE, v).apply()

    /** 是否已看过首次导览页。 */
    var onboardingSeen: Boolean
        get() = sp.getBoolean(KEY_ONBOARDING, false)
        set(v) = sp.edit().putBoolean(KEY_ONBOARDING, v).apply()

    /** 抓包黑名单（每行一个域名）。 */
    var captureBlacklist: String
        get() = sp.getString(KEY_CAPTURE_BLACK, "") ?: ""
        set(v) = sp.edit().putString(KEY_CAPTURE_BLACK, v).apply()

    /** 抓包白名单（非空时只抓白名单）。 */
    var captureWhitelist: String
        get() = sp.getString(KEY_CAPTURE_WHITE, "") ?: ""
        set(v) = sp.edit().putString(KEY_CAPTURE_WHITE, v).apply()

    companion object {
        const val KEY_MIRROR = "global_mirror"
        const val KEY_AI_BACKEND = "ai_backend_kind"
        const val KEY_MODEL_PATH = "local_model_path"
        const val KEY_API_BASE = "api_base_url"
        const val KEY_LANGUAGE = "app_language"
        const val KEY_ONBOARDING = "onboarding_seen"
        const val KEY_CAPTURE_BLACK = "capture_blacklist"
        const val KEY_CAPTURE_WHITE = "capture_whitelist"
        const val DEFAULT_API_BASE = "https://api.deepseek.com"
    }
}
