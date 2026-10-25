package com.terminalide.ai

/**
 * AI 后端类型：本地端侧小模型（离线，默认） / DeepSeek 官方 API。
 */
enum class AiBackendKind(val id: String, val displayName: String) {
    LOCAL("local", "本地端侧模型（离线）"),
    DEEPSEEK("deepseek", "DeepSeek 官方 API"),
    ;

    companion object {
        fun byId(id: String): AiBackendKind =
            entries.firstOrNull { it.id == id } ?: LOCAL
    }
}
