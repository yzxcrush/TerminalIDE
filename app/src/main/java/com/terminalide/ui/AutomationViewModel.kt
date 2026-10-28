package com.terminalide.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terminalide.TerminalIdeApp
import com.terminalide.automation.AutomationScheduler
import com.terminalide.automation.AutomationStore
import com.terminalide.automation.AutomationTask
import com.terminalide.backend.PythonBackend
import com.terminalide.core.Language
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** 自动化任务管理。 */
class AutomationViewModel : ViewModel() {

    private val app get() = TerminalIdeApp.instance

    private val _tasks = MutableStateFlow<List<AutomationTask>>(emptyList())
    val tasks: StateFlow<List<AutomationTask>> = _tasks.asStateFlow()

    private val _result = MutableStateFlow<String?>(null)
    val result: StateFlow<String?> = _result.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _tasks.value = AutomationStore.load(app)
    }

    /** 工作区里可选的脚本文件。 */
    fun scripts(): List<String> = runCatching {
        app.sandbox.languageDir("py").listFiles()
            ?.filter { it.isFile && it.name.endsWith(".py") }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }.getOrDefault(emptyList())

    fun add(name: String, script: String, minutes: Int) {
        val task = AutomationTask(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { script },
            script = script,
            intervalMinutes = minutes.coerceAtLeast(1),
            enabled = true,
        )
        val list = _tasks.value + task
        AutomationStore.save(app, list)
        AutomationScheduler.schedule(app, task)
        _tasks.value = list
    }

    fun toggle(task: AutomationTask) {
        val updated = task.copy(enabled = !task.enabled)
        val list = _tasks.value.map { if (it.id == task.id) updated else it }
        AutomationStore.save(app, list)
        AutomationScheduler.schedule(app, updated)
        _tasks.value = list
    }

    fun remove(task: AutomationTask) {
        val list = _tasks.value.filterNot { it.id == task.id }
        AutomationStore.save(app, list)
        AutomationScheduler.cancel(app, task)
        _tasks.value = list
    }

    /** 立即运行一次（测试用）。 */
    fun runNow(task: AutomationTask) {
        _running.value = true
        _result.value = "运行中…"
        viewModelScope.launch(Dispatchers.IO) {
            val out = runCatching {
                val backend = app.backends.backendFor(Language.PYTHON) as PythonBackend
                backend.runScript(task.script)
            }.getOrElse { "执行失败：${it.message}" }
            _result.value = out
            _running.value = false
        }
    }

    fun clearResult() {
        _result.value = null
    }
}
