package com.terminalide.ai

import android.content.Context
import com.terminalide.core.Language
import com.terminalide.core.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * AI 服务：按当前设置分派到本地端侧模型或 DeepSeek API，
 * 并把「当前语言 + 当前代码」织入系统提示作为上下文。
 */
class AiService(
    context: Context,
    private val settings: SettingsStore,
) {

    private val apiKeyStore = ApiKeyStore(context)
    private val local = LocalModelBackend(context, settings)
    private val deepseek = DeepSeekBackend(settings, apiKeyStore)
    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

    // ---- 后端切换 ----

    fun currentKind(): AiBackendKind = AiBackendKind.byId(settings.aiBackendKind)

    fun setKind(kind: AiBackendKind) {
        settings.aiBackendKind = kind.id
    }

    fun activeBackend(): AiBackend = when (currentKind()) {
        AiBackendKind.LOCAL -> local
        AiBackendKind.DEEPSEEK -> deepseek
    }

    suspend fun isReady(): Boolean = activeBackend().isReady()

    suspend fun probe(): ProbeResult = activeBackend().probe()

    // ---- 对话 ----

    /**
     * 发起一轮对话：system（含语言+代码）+ 历史 + 当前提问。
     * 出错时抛异常，由 ViewModel 捕获后作为错误消息展示。
     */
    suspend fun chat(
        userText: String,
        history: List<AiMessage>,
        language: Language,
        code: String,
    ): String {
        val messages = listOf(AiMessage(AiMessage.Role.SYSTEM, systemPrompt(language, code))) +
            history +
            AiMessage(AiMessage.Role.USER, userText)
        return activeBackend().chat(messages)
    }

    private fun systemPrompt(language: Language, code: String): String = """
        你是运行在 Android 设备上的编程助手。当前语言：${language.displayName}。
        回答尽量简洁；若需要给出代码，请放在一个 Markdown 代码块（fenced code block）里。

        当前编辑器代码：
        ```${language.fileExtension}
        $code
        ```
    """.trimIndent()

    /** 从助手回复里提取第一个 Markdown 代码块（供「一键插入编辑器」）。 */
    fun extractCodeBlock(reply: String): String? {
        val match = Regex("```(?:[\\w+#.-]+)?[ \\t]*\\r?\\n([\\s\\S]*?)```").find(reply)
        return match?.groupValues?.get(1)?.trimEnd()
    }

    // ---- API Key（DeepSeek） ----

    fun hasApiKey(): Boolean = !apiKeyStore.loadKey().isNullOrBlank()

    fun saveApiKey(key: String) = apiKeyStore.saveKey(key.trim())

    fun clearApiKey() = apiKeyStore.clearKey()

    /** DeepSeek Key 探测验证。 */
    suspend fun probeDeepSeek(key: String): ProbeResult = deepseek.probeWithKey(key)

    // ---- 端侧模型：运行期可选下载 ----

    fun modelPath(): String = settings.localModelPath

    fun setModelPath(path: String) {
        settings.localModelPath = path
    }

    /**
     * 下载端侧模型到 files/models，返回本地文件路径。
     * [onProgress] 回调 (已下载字节, 总字节)，总字节未知时为 -1。
     */
    suspend fun downloadModel(
        url: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): String = withContext(Dispatchers.IO) {
        val fileName = url.substringAfterLast('/').substringBefore('?').ifBlank { "model.bin" }
        val dest = File(modelsDir, fileName)
        val req = Request.Builder().url(url).get().build()
        val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("下载失败 HTTP ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("空响应体")
            val total = body.contentLength()
            var read = 0L
            body.byteStream().use { input ->
                FileOutputStream(dest).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
        }
        val path = dest.absolutePath
        settings.localModelPath = path
        path
    }
}
