package com.terminalide.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.terminalide.TerminalIdeApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var process: Process? = null
    private var readerJob: Job? = null
    private var cwd: File = app.sandbox.workspace

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun start() {
        if (process != null) return
        val p = try {
            ProcessBuilder("/system/bin/sh")
                .directory(cwd)
                .redirectErrorStream(true)
                .apply {
                    environment()["PATH"] = "/system/bin:/system/xbin:/vendor/bin:/sbin"
                }
                .start()
        } catch (t: Throwable) {
            appendOutput("启动终端失败：${t.message ?: t}\n")
            return
        }
        process = p
        _running.value = true
        appendOutput("Terminal IDE 终端（系统 sh）\n")
        appendOutput("工作目录：${cwd.absolutePath}\n")
        appendOutput("支持 ls / cd / cat / echo / grep / ps 等常用命令\n\n")
        readerJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                p.inputStream.bufferedReader().forEachLine { line ->
                    appendOutput(line + "\n")
                }
            } catch (_: Throwable) {
            }
            _running.value = false
        }
    }

    fun send(cmd: String) {
        val p = process
        if (p == null) {
            start()
            return
        }
        appendOutput("$ " + cmd + "\n")
        runCatching {
            p.outputStream.write((cmd + "\n").toByteArray())
            p.outputStream.flush()
        }
    }

    fun stop() {
        readerJob?.cancel()
        readerJob = null
        runCatching { process?.destroy() }
        process = null
        _running.value = false
    }

    fun clear() {
        _output.value = ""
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
    }
}
