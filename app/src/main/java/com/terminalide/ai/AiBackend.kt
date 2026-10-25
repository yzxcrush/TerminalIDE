package com.terminalide.ai

/**
 * AI 后端抽象：本地端侧模型 与 DeepSeek API 的公共接口。
 * 实现需在 IO 线程执行网络 / 推理。
 */
interface AiBackend {
    val kind: AiBackendKind

    /** 是否可用（本地模型已下载、API Key 已配置等）。 */
    suspend fun isReady(): Boolean

    /** 非流式对话，返回助手回复文本；出错时抛异常，由上层捕获。 */
    suspend fun chat(history: List<AiMessage>): String

    /**
     * 流式对话：边生成边通过 [onChunk] 回调增量文本，最终返回完整回复。
     * 默认实现退化为一次性返回（不支持流式的后端无需重写）。
     */
    suspend fun chatStream(history: List<AiMessage>, onChunk: (String) -> Unit): String {
        val reply = chat(history)
        if (reply.isNotEmpty()) onChunk(reply)
        return reply
    }

    /** 探测 / 验证连接与凭据。 */
    suspend fun probe(): ProbeResult
}
