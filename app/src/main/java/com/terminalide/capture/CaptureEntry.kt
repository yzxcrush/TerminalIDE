package com.terminalide.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 一次抓到的 HTTP 请求 / 响应。 */
data class CaptureEntry(
    val id: Long,
    val time: Long,
    val method: String,
    val url: String,
    val host: String,
    val scheme: String,
    val requestHeaders: String,
    val requestBody: String,
    val statusCode: Int,
    val responseHeaders: String,
    val responseBody: String,
    val error: String? = null,
) {
    /** 给 AI 的文本表示。 */
    fun toPromptText(limit: Int = 6000): String = buildString {
        append("$method $url\n\n【请求头】\n").append(requestHeaders)
        if (requestBody.isNotEmpty()) append("\n【请求体】\n").append(requestBody.take(2000))
        append("\n【响应码】").append(statusCode).append("\n【响应头】\n").append(responseHeaders)
        if (responseBody.isNotEmpty()) append("\n【响应体】\n").append(responseBody.take(2000))
        if (error != null) append("\n【错误】").append(error)
    }.take(limit)
}

/** 抓包结果的内存存储（最多保留 200 条）。 */
object CaptureStore {
    private const val MAX = 200
    private val _items = MutableStateFlow<List<CaptureEntry>>(emptyList())
    val items: StateFlow<List<CaptureEntry>> = _items.asStateFlow()
    private var seq = 0L

    fun nextId(): Long = ++seq

    fun add(entry: CaptureEntry) {
        _items.value = (listOf(entry) + _items.value).take(MAX)
    }

    fun clear() {
        _items.value = emptyList()
    }

    fun remove(id: Long) {
        _items.value = _items.value.filterNot { it.id == id }
    }
}
