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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

    /** 标准输入：运行前在这里填写，喂给程序的 input()/scanf。 */
    private val _stdin = MutableStateFlow("")
    val stdin: StateFlow<String> = _stdin.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _lastResult = MutableStateFlow<RunResult?>(null)
    val lastResult: StateFlow<RunResult?> = _lastResult.asStateFlow()

    private val _currentFile = MutableStateFlow<String?>(null)
    val currentFile: StateFlow<String?> = _currentFile.asStateFlow()

    private val _hasError = MutableStateFlow(false)
    val hasError: StateFlow<Boolean> = _hasError.asStateFlow()

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
        _currentFile.value = null
        _hasError.value = false
    }

    private var autoSaveJob: Job? = null

    fun updateCode(text: String) {
        _code.value = text
        // 自动保存（1.5s 防抖），避免忘记手动保存丢代码
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1500)
            withContext(Dispatchers.IO) {
                val file = _currentFile.value?.let { File(it) }
                    ?: File(
                        sandbox.languageDir(_language.value.fileExtension),
                        "main.${_language.value.fileExtension}"
                    )
                runCatching { file.writeText(text) }
            }
        }
    }

    /** 追加输出（限制最大长度，避免超大输出时字符串拼接 O(n²) 卡顿）。 */
    private fun appendOutput(text: String) {
        val cur = _output.value + text
        _output.value = if (cur.length > MAX_OUTPUT_CHARS) cur.takeLast(MAX_OUTPUT_CHARS) else cur
    }

    fun updateStdin(text: String) {
        _stdin.value = text
    }

    @Volatile
    private var runningBackend: com.terminalide.backend.LanguageBackend? = null

    /** 运行中向程序送入一行标准输入（交互式控制台）。 */
    fun sendStdin(line: String) {
        runningBackend?.sendInput(line)
    }

    /** 从外部（文件管理器「打开方式」）载入源码，并按扩展名切换语言。 */
    fun openExternal(text: String, fileName: String?) {
        val lang = when {
            fileName?.endsWith(".py", ignoreCase = true) == true -> Language.PYTHON
            fileName?.endsWith(".cpp", ignoreCase = true) == true ||
                fileName?.endsWith(".cc", ignoreCase = true) == true ||
                fileName?.endsWith(".cxx", ignoreCase = true) == true -> Language.CPP
            fileName?.endsWith(".c", ignoreCase = true) == true -> Language.C
            else -> _language.value
        }
        if (lang != _language.value) {
            codeByLang[_language.value] = _code.value
            _language.value = lang
        }
        _code.value = text
        codeByLang[lang] = text
        _output.value = ""
        _lastResult.value = null
        _currentFile.value = null
    }

    fun run() {
        if (_isRunning.value) return
        val backend = backends.backendFor(_language.value)
        val code = _code.value
        val stdin = _stdin.value
        _output.value = ""
        _lastResult.value = null
        _isRunning.value = true
        _hasError.value = false

        outputJob?.cancel()
        runningBackend = backend
        outputJob = viewModelScope.launch {
            // UNDISPATCHED：让 collector 在 run 之前先完成订阅，避免丢实时输出
            val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
                backend.output.collect { chunk ->
                    val prefix = if (chunk.stream == OutputChunk.Stream.STDERR) "[stderr] " else ""
                    appendOutput(prefix + chunk.text)
                }
            }
            try {
                val result = backend.run(code, stdin)
                _lastResult.value = result
                appendOutput("\n—— 退出码 ${result.exitCode} · 耗时 ${result.durationMs}ms ——\n")
                _hasError.value = result.exitCode != 0 ||
                    _output.value.contains("Traceback (most recent call last)")
                // 内置错误知识库：离线给「原因 + 怎么修」
                if (_hasError.value) {
                    com.terminalide.core.ErrorKnowledge.hintFor(_output.value)?.let { appendOutput(it) }
                }
                // 变量检查器：运行后列出所有变量
                if (backend is com.terminalide.backend.PythonBackend) {
                    runCatching {
                        val json = backend.lastVarsJson() ?: return@runCatching
                        val arr = org.json.JSONArray(json)
                        if (arr.length() > 0) {
                            val sb = StringBuilder("\n—— 变量 ——\n")
                            for (i in 0 until arr.length()) {
                                val o = arr.getJSONObject(i)
                                sb.append(o.getString("name")).append(" = ").append(o.getString("value")).append('\n')
                            }
                            appendOutput(sb.toString())
                        }
                    }
                }
                // 长脚本运行完成 → 通知栏提醒
                if (result.durationMs >= com.terminalide.core.NotificationHelper.MIN_DURATION_MS) {
                    com.terminalide.core.NotificationHelper.notifyRunDone(
                        app, result.exitCode, result.durationMs,
                    )
                }
            } catch (t: kotlinx.coroutines.CancellationException) {
                // 用户主动停止：静默退出，不打印协程取消异常
            } catch (t: Throwable) {
                appendOutput("\n[运行异常] ${t}\n")
            } finally {
                collectJob.cancel()
                backend.closeInput()
                runningBackend = null
                _isRunning.value = false
            }
        }
    }

    fun stop() {
        // 先让阻塞中的 input() 收到 EOF，否则 Python 线程会一直卡在 readLine()
        runningBackend?.abort()
        runningBackend?.closeInput()
        runningBackend?.reset()
        runningBackend = null
        outputJob?.cancel()
        _isRunning.value = false
        _hasError.value = false
        appendOutput("\n[已手动停止]\n")
    }

    fun clearOutput() {
        _output.value = ""
        _lastResult.value = null
    }

    fun save() {
        viewModelScope.launch(Dispatchers.IO) {
            val file = _currentFile.value?.let { File(it) }
                ?: File(
                    sandbox.languageDir(_language.value.fileExtension),
                    "main.${_language.value.fileExtension}"
                )
            file.parentFile?.mkdirs()
            file.writeText(_code.value)
            _currentFile.value = file.absolutePath
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
            _currentFile.value = path
            _output.value = ""
            _lastResult.value = null
        }
    }

    fun newFile(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = sandbox.languageDir(_language.value.fileExtension)
            val file = File(dir, name.ifBlank { "main.${_language.value.fileExtension}" })
            file.writeText(_language.value.template)
            _code.value = file.readText()
            _currentFile.value = file.absolutePath
            refreshFiles()
        }
    }

    /** 删除文件（文件树长按菜单）。 */
    fun deleteFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val f = File(path)
            if (f.isFile) f.delete()
            if (_currentFile.value == path) _currentFile.value = null
            refreshFiles()
        }
    }

    /** 重命名文件（文件树长按菜单）。 */
    fun renameFile(path: String, newName: String) {
        val name = newName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val f = File(path)
            val target = File(f.parentFile, name)
            if (f.isFile && target.absolutePath != path) {
                f.renameTo(target)
                if (_currentFile.value == path) _currentFile.value = target.absolutePath
            }
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

    companion object {
        /** 控制台输出最大保留字符数（超出丢弃最旧的，防止超大输出卡顿）。 */
        private const val MAX_OUTPUT_CHARS = 300_000
    }
}
