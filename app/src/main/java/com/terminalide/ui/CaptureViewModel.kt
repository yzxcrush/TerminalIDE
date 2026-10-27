package com.terminalide.ui

import android.content.Context
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terminalide.TerminalIdeApp
import com.terminalide.capture.CaptureEngine
import com.terminalide.capture.CaptureService
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
    private val ca: CaManager get() = CaptureEngine.caManager(app)
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    private val _running = CaptureEngine.running
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _status = MutableStateFlow("点「开始抓包」后，把系统代理或客户端代理指向 127.0.0.1:$PORT")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _analyzing = MutableStateFlow(false)
    val analyzing: StateFlow<Boolean> = _analyzing.asStateFlow()

    private val _aiResult = MutableStateFlow<String?>(null)
    val aiResult: StateFlow<String?> = _aiResult.asStateFlow()

    private val _replayResult = MutableStateFlow<String?>(null)
    val replayResult: StateFlow<String?> = _replayResult.asStateFlow()

    /** 一键隐藏：脱敏显示（token / cookie / 密码 等打码）。 */
    private val _hidden = MutableStateFlow(false)
    val hidden: StateFlow<Boolean> = _hidden.asStateFlow()

    fun toggleHidden() {
        _hidden.value = !_hidden.value
    }

    private val _blacklist = MutableStateFlow("")
    val blacklist: StateFlow<String> = _blacklist.asStateFlow()

    private val _whitelist = MutableStateFlow("")
    val whitelist: StateFlow<String> = _whitelist.asStateFlow()

    init {
        // 默认把微信/QQ/支付宝/银联放黑名单，避免误抓
        val defBlack = app.settings.captureBlacklist.ifBlank {
            com.terminalide.capture.CaptureFilter.format(
                com.terminalide.capture.CaptureFilter.SENSITIVE_PRESET
            )
        }
        val white = app.settings.captureWhitelist
        _blacklist.value = defBlack
        _whitelist.value = white
        com.terminalide.capture.CaptureFilter.blacklist = com.terminalide.capture.CaptureFilter.parse(defBlack)
        com.terminalide.capture.CaptureFilter.whitelist = com.terminalide.capture.CaptureFilter.parse(white)
        app.settings.captureBlacklist = defBlack
        app.settings.captureWhitelist = white
    }

    /** 保存过滤规则。 */
    fun saveFilter(black: String, white: String) {
        _blacklist.value = black
        _whitelist.value = white
        com.terminalide.capture.CaptureFilter.blacklist = com.terminalide.capture.CaptureFilter.parse(black)
        com.terminalide.capture.CaptureFilter.whitelist = com.terminalide.capture.CaptureFilter.parse(white)
        app.settings.captureBlacklist = black
        app.settings.captureWhitelist = white
    }

    val items: StateFlow<List<CaptureEntry>> = CaptureStore.items

    init {
        // 启动自检：如果系统代理还指着我们但抓包没开，自动清掉（防止 App 被杀后断网）
        // 带 10 秒超时，避免 root 未授权时卡住
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.withTimeoutOrNull(10_000) {
                runCatching { clearStaleProxyIfNeeded() }
            }
        }
    }

    /** 打开抓包面板时才做残留代理检查（避免每次启动 App 都弹 root 授权）。 */
    fun onSheetOpened() {
        viewModelScope.launch(Dispatchers.IO) { clearStaleProxyIfNeeded() }
    }

    private fun clearStaleProxyIfNeeded() {
        runCatching {
            val out = execRoot("settings get global http_proxy").trim()
            if (out.contains("8888") && !CaptureEngine.isRunning()) {
                execRoot("settings put global http_proxy :0")
            }
        }
    }

    // ---------- 代理启停 ----------

    fun start() {
        CaptureService.start(app)
        _status.value = "抓包已启动：127.0.0.1:${CaptureEngine.PORT}\n（前台服务运行中，通知栏可停止）"
    }

    fun stop() {
        CaptureService.stop(app)
        // 服务 onDestroy 也会清，这里再兜底一次
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { execRoot("settings put global http_proxy :0") }
        }
        _status.value = "已停止（系统代理已自动清除）"
    }

    /** 一键清代理：清系统代理 + 停止抓包。 */
    fun clearProxyAndStop(onDone: (String) -> Unit) {
        CaptureService.stop(app)
        _running.value = false
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { execRoot("settings put global http_proxy :0") }
            withContext(Dispatchers.Main) {
                onDone("✅ 系统代理已清除，网络恢复直连\n抓包代理已停止")
            }
        }
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

    /** 用 root 安装 CA 到系统级（Android 14+ 走 APEX 注入）。 */
    fun installCaToSystem(onDone: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val der = ca.caDer()
            if (der == null) {
                withContext(Dispatchers.Main) { onDone("CA 未生成") }
                return@launch
            }
            val hash = subjectHash(der)
            val pem = buildPem(der)
            val tmp = File(app.cacheDir, "ca-install").apply { mkdirs() }
            val pemFile = File(tmp, "$hash.0").apply { writeText(pem) }
            // 完整流程：选证书源 -> tmpfs -> 复制 -> 校验 -> 挂载到 APEX 各命名空间
            val script = """
                SRC=""
                if [ -d /apex/com.android.conscrypt@360499999/cacerts ] && [ ${'$'}(ls /apex/com.android.conscrypt@360499999/cacerts/*.0 2>/dev/null | wc -l) -gt 50 ]; then
                  SRC=/apex/com.android.conscrypt@360499999/cacerts
                elif [ ${'$'}(ls /apex/com.android.conscrypt/cacerts/*.0 2>/dev/null | wc -l) -gt 50 ]; then
                  SRC=/apex/com.android.conscrypt/cacerts
                fi
                [ -z "${'$'}SRC" ] && { echo "NO_SOURCE"; exit 1; }
                T=/data/local/tmp/terminalide-ca-app
                umount ${'$'}T 2>/dev/null; rm -rf ${'$'}T; mkdir -p ${'$'}T
                mount -t tmpfs tmpfs ${'$'}T || { echo "MOUNT_FAIL"; exit 1; }
                cp -a ${'$'}SRC/*.0 ${'$'}T/ 2>/dev/null
                cp -f "${pemFile.absolutePath}" ${'$'}T/
                CNT=${'$'}(ls ${'$'}T/*.0 2>/dev/null | wc -l)
                [ "${'$'}CNT" -lt 50 ] && { umount ${'$'}T 2>/dev/null; echo "COPY_FAIL"; exit 1; }
                chmod 644 ${'$'}T/* 2>/dev/null
                chcon -R u:object_r:system_security_cacerts_file:s0 ${'$'}T 2>/dev/null
                mount --bind ${'$'}T /apex/com.android.conscrypt/cacerts 2>/dev/null
                nsenter --mount=/proc/1/ns/mnt -- mount --bind ${'$'}T /apex/com.android.conscrypt/cacerts 2>/dev/null
                for z in zygote zygote64; do
                  for pid in ${'$'}(pidof ${'$'}z 2>/dev/null); do
                    nsenter --mount=/proc/${'$'}pid/ns/mnt -- mount --bind ${'$'}T /apex/com.android.conscrypt/cacerts 2>/dev/null
                  done
                done
                ls /apex/com.android.conscrypt/cacerts/$hash.0 && echo "CA_OK_${'$'}CNT"
            """.trimIndent()
            val r = runCatching { execRoot(script) }
            val out = r.getOrNull().orEmpty()
            val msg = when {
                out.contains("CA_OK_") -> "✅ CA 已装到系统级（${'$'}out 个证书）\n重启相关 App 后生效；重启手机后需重装"
                out.contains("NO_SOURCE") -> "❌ 找不到可用的系统证书源"
                out.contains("COPY_FAIL") -> "❌ 复制失败，已中止（未挂载，系统证书未被破坏）"
                else -> "⚠️ 安装失败：${r.exceptionOrNull()?.message ?: out.take(200)}"
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
                append(maskEntry(entry).toPromptText())
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

    /**
     * 执行 root 命令：把脚本写到临时文件再 su 执行，避免嵌套引号被 shell 解析坏。
     */
    private fun execRoot(script: String): String {
        val f = File(app.cacheDir, "root_cmd.sh")
        f.writeText("#!/system/bin/sh\n$script\n")
        val process = ProcessBuilder("su", "-c", "sh ${f.absolutePath}").start()
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        process.waitFor()
        if (err.isNotBlank() && out.isBlank()) throw IllegalStateException(err.take(300))
        return out
    }

    override fun onCleared() {
        // 代理由前台服务持有，这里只兜底清理可能残留的系统代理
        if (!CaptureEngine.isRunning()) {
            runCatching { execRoot("settings put global http_proxy :0") }
        }
    }

    // ---------- 脱敏 ----------

    private val SENSITIVE = setOf(
        "authorization", "cookie", "set-cookie", "token", "x-token",
        "x-auth-token", "x-api-key", "api-key", "x-csrf-token", "sessionid",
    )

    /** 按隐私模式返回脱敏后的条目。 */
    fun maskEntry(entry: CaptureEntry): CaptureEntry {
        if (!_hidden.value) return entry
        return entry.copy(
            requestHeaders = maskHeaders(entry.requestHeaders),
            responseHeaders = maskHeaders(entry.responseHeaders),
            requestBody = maskBody(entry.requestBody),
            responseBody = maskBody(entry.responseBody),
        )
    }

    private fun maskHeaders(raw: String): String = raw.lineSequence().joinToString("\n") { line ->
        val idx = line.indexOf(':')
        if (idx > 0 && line.substring(0, idx).trim().lowercase() in SENSITIVE) {
            line.substring(0, idx + 1) + " ***（已隐藏）***"
        } else line
    }

    private fun maskBody(raw: String): String {
        if (raw.isEmpty()) return raw
        // 简单规则：把 xxx=值 / "token": "值" 打码
        return raw
            .replace(Regex("(?i)(token|password|passwd|pwd|cookie|authorization|secret)([\"']?\\s*[:=]\\s*[\"']?)[^&\"',}\\s]+"), "$1$2***")
            .take(2000)
    }

    companion object {
        const val PORT = CaptureEngine.PORT
    }
}
