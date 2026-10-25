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

    /** 探测 / 验证连接与凭据。 */
    suspend fun probe(): ProbeResult
}
