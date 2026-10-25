package com.terminalide.backend

import com.chaquo.python.Python
import com.terminalide.core.Language
import com.terminalide.core.RunResult
import com.terminalide.core.Sandbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Python 与 UI 之间的输入输出桥：
 *  - readLine() 阻塞等待 UI 送入的一行（供 input() 使用）
 *  - emitOut/emitErr 把 sys.stdout/stderr 的内容实时推给 UI
 */
class PythonIoBridge(
    private val queue: LinkedBlockingQueue<String>,
    private val emit: (OutputChunk) -> Unit,
) {
    @Volatile
    var closed: Boolean = false

    /** Python 侧调用：阻塞直到有一行输入；closed 后返回 null 表示 EOF。 */
    fun readLine(): String? {
        while (true) {
            if (closed) return null
            val line = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
            return line
        }
    }

    fun emitOut(text: String) = emit(OutputChunk(OutputChunk.Stream.STDOUT, text))

    fun emitErr(text: String) = emit(OutputChunk(OutputChunk.Stream.STDERR, text))
}

/**
 * Python 后端：走 Chaquopy 嵌入式 CPython，在同一进程内解释执行用户代码。
 *
 * 采用交互式 IO 桥：程序运行中可以用 UI 底部控制台实时输入（input() 会阻塞等待），
 * 输出也会逐字实时显示，而不是等程序结束。
 */
class PythonBackend(private val sandbox: Sandbox) : LanguageBackend {

    override val language: Language = Language.PYTHON

    private val _output = MutableSharedFlow<OutputChunk>(extraBufferCapacity = 2048)
    override val output: SharedFlow<OutputChunk> = _output

    @Volatile
    private var bridge: PythonIoBridge? = null

    @Volatile
    private var queue: LinkedBlockingQueue<String>? = null

    override fun reset() {
        bridge?.closed = true
        bridge = null
        queue = null
    }

    override fun sendInput(line: String) {
        // 控制台输入去掉尾部换行后入队
        val clean = line.trimEnd('\n', '\r')
        queue?.offer(clean)
    }

    override fun closeInput() {
        bridge?.closed = true
    }

    override suspend fun run(code: String, stdin: String): RunResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val q = LinkedBlockingQueue<String>()
        queue = q
        val b = PythonIoBridge(q) { chunk -> _output.tryEmit(chunk) }
        bridge = b

        // 预先填写的批量 stdin 先入队
        stdin.split("\n").filter { it.isNotEmpty() }.forEach { q.offer(it) }

        var exit = 0
        try {
            val py = Python.getInstance()
            val runner = py.getModule("ide_runner")
            val res = runner.callAttr(
                "run_user",
                code,
                sandbox.sitePackages.absolutePath,
                stdin,
                b,
            )
            exit = res.asList()[0].toInt()
        } catch (t: Throwable) {
            _output.tryEmit(OutputChunk(OutputChunk.Stream.STDERR, "Python 运行错误：$t\n"))
            exit = 1
        } finally {
            b.closed = true
            bridge = null
            queue = null
        }
        RunResult(exit, "", "", System.currentTimeMillis() - start)
    }
}
