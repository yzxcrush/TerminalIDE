package com.terminalide.ui

import com.terminalide.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terminalide.TerminalIdeApp
import com.terminalide.backend.PythonBackend
import com.terminalide.core.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Python REPL（交互式解释器）状态：输入一行即执行，结果追加到控制台。
 * 使用持久命名空间，变量/import 跨多次输入保留。
 */
class ReplViewModel : ViewModel() {

    private val app get() = TerminalIdeApp.instance

    private val _output = MutableStateFlow(app.getString(R.string.repl_welcome) + "\n")
    val output: StateFlow<String> = _output.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val backend get() = app.backends.backendFor(Language.PYTHON) as? PythonBackend

    fun execute(code: String) {
        val text = code.trim()
        if (text.isEmpty() || _busy.value) return
        _busy.value = true
        _output.value = _output.value + "\n>>> " + text + "\n"
        viewModelScope.launch {
            val result = backend?.replExec(text) ?: "（Python 后端不可用）\n"
            val cur = _output.value + result
            _output.value = if (cur.length > MAX_REPL_CHARS) cur.takeLast(MAX_REPL_CHARS) else cur
            _busy.value = false
        }
    }

    fun clear() {
        _output.value = app.getString(R.string.repl_welcome) + "\n"
        viewModelScope.launch { backend?.replReset() }
    }

    private companion object {
        /** REPL 输出上限，超出丢弃最旧的，避免内存无限增长。 */
        const val MAX_REPL_CHARS = 150_000
    }
}
