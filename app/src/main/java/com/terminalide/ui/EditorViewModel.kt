package com.terminalide.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terminalide.TerminalIdeApp
import com.terminalide.backend.OutputChunk
import com.terminalide.core.FileItem
import com.terminalide.core.Language
import com.terminalide.core.RunResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

/**
 * 编辑器 + 运行 + 文件树 的 UI 状态。
 * 纯 Kotlin 状态层，供 Compose 消费；所有耗时操作在协程 IO 线程。
 */
class EditorViewModel : ViewModel() {

    private val app get() = TerminalIdeApp.instance
    private val sandbox get() = app.sandbox
    private val backends get() = app.backends

    // ---- 语言与代码 ----

    private val _language = MutableStateFlow(Language.PYTHON)
    val language: StateFlow<Language> = _language.asStateFlow()

    private val codeByLang = mutableMapOf<Language, String>().apply {
        Language.entries.forEach { put(it, it.template) }
    }

    private val _code = MutableStateFlow(Language.PYTHON.template)
    val code: StateFlow<String> = _code.asStateFlow()

    // ---- 文件树 ----

    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    // ---- 运行输出 ----

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastResult = MutableStateFlow<RunResult?>(null)
    val lastResult: StateFlow<RunResult?> = _lastResult.asStateFlow()

    private var outputJob: Job? = null

    init {
        refreshFiles()
    }

    // ---- 操作 ----

    fun switchLanguage(lang: Language) {
        if (lang == _language.value) return
        codeByLang[_language.value] = _code.value
        _language.value = lang
        _code.value = codeByLang[lang] ?: lang.template
        _output.value = ""
        _lastResult.value = null
    }

    fun updateCode(text: String) {
        _code.value = text
    }

    fun run() {
        if (_isRunning.value) return
        val backend = backends.backendFor(_language.value)
        val code = _code.value
        _output.value = ""
        _lastResult.value = null
        _isRunning.value = true

        outputJob?.cancel()
        outputJob = viewModelScope.launch {
            // UNDISPATCHED：让 collector 在 run 之前先完成订阅，避免丢实时输出
            val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
                backend.output.collect { chunk ->
                    val prefix = if (chunk.stream == OutputChunk.Stream.STDERR) "[stderr] " else ""
                    _output.value = _output.value + prefix + chunk.text + "\n"
                }
            }
            try {
                val result = backend.run(code)
                _lastResult.value = result
                _output.value = _output.value +
                    "\n—— 退出码 ${result.exitCode} · 耗时 ${result.durationMs}ms ——\n"
            } catch (t: Throwable) {
                _output.value = _output.value + "\n[运行异常] ${t}\n"
            } finally {
                collectJob.cancel()
                _isRunning.value = false
            }
        }
    }

    fun stop() {
        outputJob?.cancel()
        _isRunning.value = false
        _output.value = _output.value + "\n[已手动停止]\n"
    }

    fun clearOutput() {
        _output.value = ""
        _lastResult.value = null
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(
                sandbox.languageDir(_language.value.fileExtension),
                "main.${_language.value.fileExtension}"
            )
            file.writeText(_code.value)
            refreshFiles()
        }
    }

    fun loadFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val f = File(path)
            if (!f.isFile) return@launch
            val text = f.readText()
            val lang = Language.entries.firstOrNull { it.fileExtension == f.extension }
            if (lang != null && lang != _language.value) {
                codeByLang[_language.value] = _code.value
                _language.value = lang
                codeByLang[lang] = text
            }
            _code.value = text
        }
    }

    fun newFile(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = sandbox.languageDir(_language.value.fileExtension)
            val file = File(dir, name.ifBlank { "main.${_language.value.fileExtension}" })
            file.writeText(_language.value.template)
            _code.value = file.readText()
            refreshFiles()
        }
    }

    fun refreshFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            _files.value = listRecursive(sandbox.workspace)
        }
    }

    private fun listRecursive(dir: File): List<FileItem> {
        val children = dir.listFiles() ?: return emptyList()
        return children
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .flatMap { f ->
                if (f.isDirectory) {
                    listOf(FileItem(f.name, f.absolutePath, isDirectory = true)) + listRecursive(f)
                } else {
                    val lang = Language.entries.firstOrNull { it.fileExtension == f.extension }
                    listOf(FileItem(f.name, f.absolutePath, isDirectory = false, language = lang))
                }
            }
    }
}
