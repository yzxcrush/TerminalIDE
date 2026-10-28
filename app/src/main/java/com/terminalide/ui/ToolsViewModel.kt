package com.terminalide.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terminalide.TerminalIdeApp
import com.terminalide.backend.PythonBackend
import com.terminalide.core.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** 脚本变 App：把工作区里的顶层函数变成可填表运行的小工具。 */
class ToolsViewModel : ViewModel() {

    private val app get() = TerminalIdeApp.instance
    private val backend get() = app.backends.backendFor(Language.PYTHON) as PythonBackend

    data class ToolParam(val name: String, val type: String)
    data class ToolInfo(
        val file: String,
        val func: String,
        val params: List<ToolParam>,
        val doc: String,
    )

    private val _tools = MutableStateFlow<List<ToolInfo>>(emptyList())
    val tools: StateFlow<List<ToolInfo>> = _tools.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _output = MutableStateFlow<String?>(null)
    val output: StateFlow<String?> = _output.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val json = backend.listTools()
            _tools.value = parse(json)
        }
    }

    private fun parse(json: String): List<ToolInfo> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val params = o.optJSONArray("params")?.let { ps ->
                (0 until ps.length()).map { j ->
                    val p = ps.getJSONObject(j)
                    ToolParam(p.optString("name"), p.optString("type", "str"))
                }
            } ?: emptyList()
            ToolInfo(
                file = o.optString("file"),
                func = o.optString("func"),
                params = params,
                doc = o.optString("doc"),
            )
        }
    }.getOrDefault(emptyList())

    fun run(tool: ToolInfo, values: Map<String, String>) {
        _running.value = true
        _output.value = "运行中…"
        viewModelScope.launch(Dispatchers.IO) {
            val kwargs = JSONObject()
            tool.params.forEach { p ->
                val raw = values[p.name] ?: ""
                when {
                    p.type.contains("int") -> kwargs.put(p.name, raw.toIntOrNull() ?: 0)
                    p.type.contains("float") -> kwargs.put(p.name, raw.toFloatOrNull() ?: 0f)
                    p.type.contains("bool") -> kwargs.put(p.name, raw.equals("true", true))
                    else -> kwargs.put(p.name, raw)
                }
            }
            val out = backend.runTool(tool.file, tool.func, kwargs.toString())
            _output.value = out
            _running.value = false
        }
    }

    fun clearOutput() {
        _output.value = null
    }
}
