package com.terminalide.ui

import android.content.Context
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terminalide.TerminalIdeApp
import com.terminalide.capture.CaptureEntry
import com.terminalide.capture.CaptureProxy
import com.terminalide.capture.CaptureStore
import com.terminalide.capture.CaManager
import com.terminalide.core.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 抓包功能的状态与操作：代理启停、CA 导出、系统代理、AI 解析、请求重放。
 */
class CaptureViewModel : ViewModel() {

    private val app get() = TerminalIdeApp.instance
    private val ca by lazy { CaManager(app) }
    private val proxy by lazy { CaptureProxy(ca, PORT) }
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _status = MutableStateFlow("点「开始抓包」后，把系统代理或客户端代理指向 127.0.0.1:$PORT")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _analyzing = MutableStateFlow(false)
    val analyzing: StateFlow<Boolean> = _analyzing.asStateFlow()

    private val _aiResult = MutableStateFlow<String?>(null)
    val aiResult: StateFlow<String?> = _aiResult.asStateFlow()

    private val _replayResult = MutableStateFlow<String?>(null)
    val replayResult: StateFlow<String?> = _replayResult.asStateFlow()

    val items: StateFlow<List<CaptureEntry>> = CaptureStore.items

    // ---------- 代理启停 ----------

    fun start() {
        ca.ensure()
        val ok = proxy.start()
        _running.value = ok
        _status.value = if (ok) {
            "抓包已启动：127.0.0.1:$PORT\n需要 HTTPS 解密请先导出并安装 CA 证书"
        } else {
            "启动失败：端口 $PORT 可能被占用"
        }
    }

    fun stop() {
        proxy.stop()
        _running.value = false
        _status.value = "已停止"
    }

    fun clear() {
        CaptureStore.clear()
        _aiResult.value = null
        _replayResult.value = null
    }

    // ---------- CA 与系统代理（需要 root）----------

    /** 把 CA 证书导出到 /sdcard/Download，方便手动安装。 */
    fun exportCa(onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val der = ca.caDer()
            if (der == null) {
                withContext(Dispatchers.Main) { onDone("导出失败：CA 未生成") }
                return@launch
            }
            val pem = buildPem(der)
            val out = File("/sdcard/Download/terminalide-ca.crt")
            val msg = runCatching {
                out.writeText(pem)
                "已导出：${out.absolutePath}\n\n手动安装：设置 → 安全 → 加密与凭据 → 安装证书 → CA 证书"
            }.getOrElse {
                val cache = File(app.cacheDir, "terminalide-ca.crt")
                cache.writeText(pem)
                "已导出到缓存：${cache.absolutePath}"
            }
            withContext(Dispatchers.Main) { onDone(msg) }
        }
    }

    /** 用 root 安装 CA 到系统证书（挂载覆盖，重启后失效）。 */
    fun installCaToSystem(onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val der = ca.caDer()
            if (der == null) {
                withContext(Dispatchers.Main) { onDone("CA 未生成") }
                return@launch
            }
            val hash = subjectHash(der)
            val pem = buildPem(der)
            val tmp = File(app.cacheDir, "ca-install")
            tmp.mkdirs()
            val pemFile = File(tmp, "$hash.0").apply { writeText(pem) }
            val script = """
                set -e
                mkdir -p /data/local/tmp/ticacerts
                rm -f /data/local/tmp/ticacerts/* 2>/dev/null || true
                cp /system/etc/security/cacerts/* /data/local/tmp/ticacerts/ 2>/dev/null || true
                cp "${pemFile.absolutePath}" /data/local/tmp/ticacerts/$hash.0
                mount -t tmpfs tmpfs /system/etc/security/cacerts 2>/dev/null || true
                cp /data/local/tmp/ticacerts/* /system/etc/security/cacerts/
                chmod 644 /system/etc/security/cacerts/*
                ls /system/etc/security/cacerts/$hash.0
            """.trimIndent()
            val r = runCatching { execRoot(script) }
            val msg = if (r.isSuccess && r.getOrNull()?.contains(hash) == true) {
                "✅ CA 已安装到系统证书（$hash.0）\n重启相关 App 后生效（重启失效，需重新安装）"
            } else {
                "⚠️ 系统证书安装失败：${r.exceptionOrNull()?.message ?: r.getOrNull()}\n请改用「导出 CA」手动安装"
            }
            withContext(Dispatchers.Main) { onDone(msg) }
        }
    }

    /** 设置系统 HTTP 代理（需要 root）。 */
    fun setSystemProxy(onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val r = runCatching {
                execRoot("settings put global http_proxy 127.0.0.1:$PORT")
            }
            val msg = if (r.isSuccess) "✅ 已设置系统代理 127.0.0.1:$PORT（浏览器等生效）"
            else "❌ 设置失败（需要 root）：${r.exceptionOrNull()?.message}"
            withContext(Dispatchers.Main) { onDone(msg) }
        }
    }

    /** 清除系统代理。 */
    fun clearSystemProxy(onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                execRoot("settings put global http_proxy :0; settings delete global http_proxy")
            }
            withContext(Dispatchers.Main) { onDone("已清除系统代理") }
        }
    }

    // ---------- AI 解析 ----------

    fun analyze(entry: CaptureEntry) {
        if (_analyzing.value) return
        _analyzing.value = true
        _aiResult.value = null
        viewModelScope.launch {
            val prompt = buildString {
                append("下面是我抓到的一个 HTTP 请求/响应，请帮我分析：\n")
                append("1) 这个接口是做什么的\n")
                append("2) 请求头/参数里哪些是关键（token、签名、时间戳等）\n")
                append("3) 用 Python requests 怎么写一段等价的请求代码\n\n")
                append(entry.toPromptText())
            }
            val result = runCatching {
                app.aiService.chat(prompt, emptyList(), Language.PYTHON, "")
            }.getOrElse { "⚠️ 分析失败：${it.message ?: it}" }
            _aiResult.value = result
            _analyzing.value = false
        }
    }

    fun clearAiResult() {
        _aiResult.value = null
    }

    // ---------- 重放 ----------

    fun replay(entry: CaptureEntry) {
        _replayResult.value = "发送中…"
        viewModelScope.launch(Dispatchers.IO) {
            val body = entry.requestBody.takeIf { it.isNotEmpty() }
            val reqBuilder = Request.Builder().url(entry.url)
            val skip = setOf(
                "host", "content-length", "transfer-encoding", "connection",
                "accept-encoding", "content-encoding", "proxy-connection",
            )
            entry.requestHeaders.lineSequence().forEach { line ->
                val idx = line.indexOf(':')
                if (idx > 0) {
                    val name = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim()
                    if (name.isNotEmpty() && name.lowercase() !in skip && value.isNotEmpty()) {
                        runCatching { reqBuilder.header(name, value) }
                    }
                }
            }
            val method = entry.method.uppercase()
            val request = runCatching {
                when {
                    body != null -> reqBuilder.method(method, body.toRequestBody("application/octet-stream".toMediaTypeOrNull())).build()
                    method == "GET" -> reqBuilder.get().build()
                    method == "HEAD" -> reqBuilder.head().build()
                    method == "DELETE" -> reqBuilder.delete().build()
                    else -> reqBuilder.method(method, ByteArray(0).toRequestBody(null)).build()
                }
            }.getOrElse {
                _replayResult.value = "构造请求失败：${it.message}"
                return@launch
            }
            val text = runCatching {
                http.newCall(request).execute().use { resp ->
                    val b = resp.body?.string().orEmpty()
                    "HTTP ${resp.code}\n\n${b.take(8000)}"
                }
            }.getOrElse { "⚠️ 重放失败：${it.message ?: it}" }
            _replayResult.value = text
        }
    }

    fun clearReplayResult() {
        _replayResult.value = null
    }

    // ---------- 工具 ----------

    private fun buildPem(der: ByteArray): String = buildString {
        append("-----BEGIN CERTIFICATE-----\n")
        append(Base64.encodeToString(der, Base64.NO_WRAP).chunked(64).joinToString("\n"))
        append("\n-----END CERTIFICATE-----\n")
    }

    /** OpenSSL 的 X509 subject hash（Android 系统证书文件名格式）。 */
    private fun subjectHash(der: ByteArray): String {
        return runCatching {
            val cf = java.security.cert.CertificateFactory.getInstance("X.509")
            val cert = cf.generateCertificate(java.io.ByteArrayInputStream(der)) as java.security.cert.X509Certificate
            val digest = java.security.MessageDigest.getInstance("SHA-1")
                .digest(cert.subjectX500Principal.encoded)
            val hash = (digest[0].toLong() and 0xFF) or
                ((digest[1].toLong() and 0xFF) shl 8) or
                ((digest[2].toLong() and 0xFF) shl 16) or
                ((digest[3].toLong() and 0xFF) shl 24)
            String.format("%08x", hash)
        }.getOrDefault(System.currentTimeMillis().toString())
    }

    private fun execRoot(script: String): String {
        val process = ProcessBuilder("sh", "-c", "su -c 'sh -c \"$script\"'").start()
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        process.waitFor()
        if (err.isNotBlank()) throw IllegalStateException(err.take(300))
        return out
    }

    override fun onCleared() {
        proxy.stop()
    }

    companion object {
        const val PORT = 8888
    }
}
