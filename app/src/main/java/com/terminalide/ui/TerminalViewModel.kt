package com.terminalide.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terminalide.TerminalIdeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 精简终端：用系统 `/system/bin/sh` 起一个 shell，在 App 进程权限下执行命令。
 *
 * 说明：这是 Android 自带的 toybox sh（ls/cat/echo/cd/grep 等常用命令可用），
 * **不是完整 Termux 环境**（没有 pkg/apt、没有 Termux 的软件仓库与 root 化路径）。
 */
class TerminalViewModel : ViewModel() {

    private val app get() = TerminalIdeApp.instance
    @Volatile
    private var process: Process? = null
    private var cwd: File = app.sandbox.workspace

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** 命令历史（↑/↓ 翻阅）。 */
    private val history = mutableListOf<String>()
    private var historyIndex = -1

    /** 防止并发重复启动（点发送时 start() 会被调用多次）。 */
    @Volatile
    private var starting = false

    /** 启动前积压的命令，shell 就绪后补发。 */
    private val pendingCommands = java.util.concurrent.LinkedBlockingQueue<String>()

    fun start() {
        synchronized(this) {
            if (process != null || starting) return
            starting = true
        }
        // 进程启动是阻塞操作，放到 IO 线程，避免卡主线程
        viewModelScope.launch(Dispatchers.IO) {
            val p = try {
                ProcessBuilder("/system/bin/sh")
                    .directory(cwd)
                    .redirectErrorStream(true)
                    .apply {
                        environment()["PATH"] = "/system/bin:/system/xbin:/vendor/bin:/sbin"
                    }
                    .start()
            } catch (t: Throwable) {
                starting = false
                appendOutput("启动终端失败：${t.message ?: t}\n")
                return@launch
            }
            process = p
            starting = false
            _running.value = true
            appendOutput("Terminal IDE 终端（系统 sh）\n")
            appendOutput("工作目录：${cwd.absolutePath}\n")
            appendOutput("支持 ls / cd / cat / echo / grep / ps 等常用命令\n\n")
            // 补发启动前输入的命令
            while (true) {
                val queued = pendingCommands.poll() ?: break
                runCatching {
                    p.outputStream.write((queued + "\n").toByteArray())
                    p.outputStream.flush()
                }
            }
            try {
                p.inputStream.bufferedReader().forEachLine { line ->
                    appendOutput(stripAnsi(line) + "\n")
                }
            } catch (_: Throwable) {
            }
            // shell 退出（如输入 exit）后重置，允许再次启动
            if (process === p) process = null
            starting = false
            _running.value = false
        }
    }

    fun send(cmd: String) {
        history.add(cmd)
        historyIndex = history.size
        appendOutput("$ " + cmd + "\n")
        val p = process
        if (p == null) {
            // 还没启动/刚退出：排队，等 shell 就绪后补发
            pendingCommands.offer(cmd)
            start()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                p.outputStream.write((cmd + "\n").toByteArray())
                p.outputStream.flush()
            }
        }
    }

    fun stop() {
        starting = false
        pendingCommands.clear()
        runCatching { process?.destroy() }
        process = null
        _running.value = false
    }

    fun clear() {
        _output.value = ""
    }

    /** 上一条历史命令。 */
    fun historyUp(): String {
        if (history.isEmpty()) return ""
        historyIndex = (historyIndex - 1).coerceIn(0, history.size - 1)
        return history[historyIndex]
    }

    /** 下一条历史命令。 */
    fun historyDown(): String {
        if (history.isEmpty()) return ""
        historyIndex = (historyIndex + 1).coerceIn(0, history.size)
        return history.getOrNull(historyIndex) ?: ""
    }

    private fun appendOutput(text: String) {
        val cur = _output.value + text
        _output.value = if (cur.length > MAX_OUTPUT) cur.takeLast(MAX_OUTPUT) else cur
    }

    override fun onCleared() {
        stop()
    }

    private companion object {
        const val MAX_OUTPUT = 200_000
        /** 匹配 ANSI 转义序列（颜色/光标控制），避免终端里出现乱码。 */
        val ANSI_REGEX = Regex("\u001B\\[[0-9;?]*[a-zA-Z]|\u001B\\][^\u0007]*\u0007")

        fun stripAnsi(line: String): String = ANSI_REGEX.replace(line, "")
    }
}
