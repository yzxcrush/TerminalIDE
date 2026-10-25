package com.terminalide.ai

/**
 * 一条对话消息。
 */
data class AiMessage(val role: Role, val content: String) {
    enum class Role {
        SYSTEM, USER, ASSISTANT;

        /** OpenAI 协议线格式。 */
        val wire: String
            get() = when (this) {
                SYSTEM -> "system"
                USER -> "user"
                ASSISTANT -> "assistant"
            }
    }
}

/**
 * 后端探测 / 验证结果。
 */
data class ProbeResult(val ok: Boolean, val message: String)
