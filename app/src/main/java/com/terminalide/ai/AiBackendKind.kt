package com.terminalide.ai

/**
 * AI 后端类型：
 *  - 本地端侧小模型（离线，默认）
 *  - DeepSeek-V3（API，deepseek-chat）
 *  - DeepSeek-R1（API，deepseek-reasoner，推理模型）
 */
enum class AiBackendKind(val id: String, val displayName: String) {
    LOCAL("local", "本地端侧模型（离线）"),
    DEEPSEEK("deepseek", "DeepSeek-V3（API）"),
    DEEPSEEK_R1("deepseek_r1", "DeepSeek-R1（推理 API）"),
    ;

    companion object {
        fun byId(id: String): AiBackendKind =
            entries.firstOrNull { it.id == id } ?: LOCAL
    }
}
