package com.terminalide.capture

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * 抓包结果存储：内存 + 落盘持久化（重启不丢）。
 */
object CaptureStore {

    private const val MAX = 200
    private const val FILE_NAME = "captures.json"

    private val executor = Executors.newSingleThreadExecutor()
    private var file: File? = null

    private val _items = MutableStateFlow<List<CaptureEntry>>(emptyList())
    val items: StateFlow<List<CaptureEntry>> = _items.asStateFlow()

    private var seq = 0L

    /** App 启动时调用：加载历史记录。 */
    fun init(ctx: Context) {
        if (file != null) return
        file = File(ctx.applicationContext.filesDir, FILE_NAME)
        executor.execute { load() }
    }

    private fun load() {
        runCatching {
            val f = file ?: return
            if (!f.exists()) return
            val arr = JSONArray(f.readText())
            val list = (0 until arr.length()).mapNotNull { i ->
                runCatching { fromJson(arr.getJSONObject(i)) }.getOrNull()
            }
            seq = list.maxOfOrNull { it.id } ?: 0L
            _items.value = list
        }
    }

    fun nextId(): Long = ++seq

    fun add(entry: CaptureEntry) {
        _items.value = (listOf(entry) + _items.value).take(MAX)
        persist()
    }

    fun clear() {
        _items.value = emptyList()
        persist()
    }

    fun remove(id: Long) {
        _items.value = _items.value.filterNot { it.id == id }
        persist()
    }

    private fun persist() {
        val snapshot = _items.value
        val f = file ?: return
        executor.execute {
            runCatching {
                val arr = JSONArray()
                snapshot.forEach { arr.put(toJson(it)) }
                f.writeText(arr.toString())
            }
        }
    }

    private fun toJson(e: CaptureEntry): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("time", e.time)
        put("method", e.method)
        put("url", e.url)
        put("host", e.host)
        put("scheme", e.scheme)
        put("reqHeaders", e.requestHeaders)
        put("reqBody", e.requestBody)
        put("status", e.statusCode)
        put("respHeaders", e.responseHeaders)
        put("respBody", e.responseBody)
        put("error", e.error ?: "")
    }

    private fun fromJson(o: JSONObject): CaptureEntry = CaptureEntry(
        id = o.optLong("id"),
        time = o.optLong("time"),
        method = o.optString("method"),
        url = o.optString("url"),
        host = o.optString("host"),
        scheme = o.optString("scheme"),
        requestHeaders = o.optString("reqHeaders"),
        requestBody = o.optString("reqBody"),
        statusCode = o.optInt("status"),
        responseHeaders = o.optString("respHeaders"),
        responseBody = o.optString("respBody"),
        error = o.optString("error").ifBlank { null },
    )
}
