package com.terminalide.backend

import com.terminalide.core.Language
import com.terminalide.core.RunResult
import kotlinx.coroutines.flow.SharedFlow

/**
 * 输出块：实时/最终捕获的一行或一段输出。
 */
data class OutputChunk(val stream: Stream, val text: String) {
    enum class Stream { STDOUT, STDERR }
}

/**
 * 三语言执行后端的统一抽象。
 *
 * 实现契约：
 * - [run] 必须在协程 IO 线程执行（编译 / 运行 / 装包均耗时，不得阻塞 UI 线程）。
 * - [output] 供 UI 订阅输出：C/C++ 为真·实时（进程流逐行），Python 在结束时刻一次性发出。
 */
interface LanguageBackend {
    val language: Language

    /** 运行源码：Python 解释执行；C/C++ 为「写沙箱 → 编译 → 运行」。 */
    suspend fun run(code: String, stdin: String = ""): RunResult

    /** 输出流。 */
    val output: SharedFlow<OutputChunk>

    /** 向正在运行的程序送入一行标准输入（交互式控制台）。 */
    fun sendInput(line: String) {}

    /** 关闭输入（EOF），让阻塞中的 readline 返回。 */
    fun closeInput() {}

    /** 请求中断正在执行的程序（Python 用 sys.settrace 打断死循环）。 */
    fun abort() {}

    /** 清理上一次运行的残留输出 / 状态。 */
    fun reset()
}
