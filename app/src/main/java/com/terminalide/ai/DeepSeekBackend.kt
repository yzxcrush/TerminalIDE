package com.terminalide.ai

import com.terminalide.core.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * DeepSeek 官方 API 客户端（OpenAI 兼容协议）。
 *
 * 端点：<baseUrl>/chat/completions，模型 deepseek-chat，Bearer 鉴权。
 * Base URL 可在设置里切换（例如第三方兼容端点 / 镜像）。
 */
class DeepSeekBackend(
    private val settings: SettingsStore,
    private val apiKeyStore: ApiKeyStore,
) : AiBackend {

    override val kind: AiBackendKind = AiBackendKind.DEEPSEEK

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun isReady(): Boolean =
        !apiKeyStore.loadKey().isNullOrBlank()

    override suspend fun chat(history: List<AiMessage>): String = withContext(Dispatchers.IO) {
        val key = apiKeyStore.loadKey()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("未配置 API Key")
        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", messagesToJson(history))
            .put("stream", false)
            .put("temperature", 0.3)
            .put("max_tokens", 2048)

        val req = requestBuilder(body.toString())
            .header("Authorization", "Bearer $key")
            .build()

        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException("DeepSeek API 错误 HTTP ${resp.code}：${extractError(text)}")
            }
            parseContent(text)
        }
    }

    override suspend fun probe(): ProbeResult =
        probeWithKey(apiKeyStore.loadKey().orEmpty())

    /** 用指定 Key 做探测验证（用于「填 Key → 探测验证 → 加密存储」流程）。 */
    suspend fun probeWithKey(key: String): ProbeResult {
        if (key.isBlank()) return ProbeResult(false, "API Key 为空")
        return try {
            withContext(Dispatchers.IO) {
                val body = JSONObject()
                    .put("model", MODEL)
                    .put("messages", JSONArray().put(
                        JSONObject().put("role", "user").put("content", "ping")
                    ))
                    .put("max_tokens", 1)
                    .put("stream", false)

                val req = requestBuilder(body.toString())
                    .header("Authorization", "Bearer $key")
                    .build()

                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        ProbeResult(true, "验证通过")
                    } else {
                        ProbeResult(false, "验证失败 HTTP ${resp.code}：${extractError(text)}")
                    }
                }
            }
        } catch (t: Throwable) {
            ProbeResult(false, t.message ?: "网络错误")
        }
    }

    private fun requestBuilder(json: String): Request.Builder =
        Request.Builder()
            .url("${baseUrl()}/chat/completions")
            .header("Content-Type", "application/json; charset=utf-8")
            .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))

    private fun baseUrl(): String =
        settings.apiBaseUrl.trimEnd('/').ifBlank { SettingsStore.DEFAULT_API_BASE }

    private fun messagesToJson(history: List<AiMessage>): JSONArray =
        JSONArray().apply {
            history.forEach { m ->
                put(JSONObject().put("role", m.role.wire).put("content", m.content))
            }
        }

    private fun parseContent(text: String): String {
        val json = JSONObject(text)
        val choices = json.optJSONArray("choices")
        val first = choices?.optJSONObject(0)
            ?: throw IllegalStateException("响应缺少 choices：${text.take(200)}")
        return first.getJSONObject("message").optString("content")
    }

    private fun extractError(text: String): String = try {
        val msg = JSONObject(text).getJSONObject("error").optString("message")
        msg.ifEmpty { text.take(200) }
    } catch (t: Throwable) {
        text.take(200)
    }

    companion object {
        const val MODEL = "deepseek-chat"
    }
}
