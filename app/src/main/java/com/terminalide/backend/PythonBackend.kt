package com.terminalide.backend

import com.chaquo.python.Python
import com.terminalide.core.Language
import com.terminalide.core.RunResult
import com.terminalide.core.Sandbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext

/**
 * Python 后端：走 Chaquopy 嵌入式 CPython，在同一进程内解释执行用户代码。
 *
 * 输出捕获与异常处理委托给随 APK 打包的 `ide_runner.py` 模块，
 * 它会把 sys.stdout/stderr 切到 StringIO、注入沙箱 site-packages，并在结束后还原。
 */
class PythonBackend(private val sandbox: Sandbox) : LanguageBackend {

    override val language: Language = Language.PYTHON

    private val _output = MutableSharedFlow<OutputChunk>(extraBufferCapacity = 256)
    override val output: SharedFlow<OutputChunk> = _output

    override fun reset() {
        // 无持久状态；Python 解释器本身由 Chaquopy 托管
    }

    override suspend fun run(code: String): RunResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        var exit: Int
        var out: String
        var err: String
        try {
            val py = Python.getInstance()
            val runner = py.getModule("ide_runner")
            val res = runner.callAttr("run_user", code, sandbox.sitePackages.absolutePath)
            val list = res.asList()
            exit = list[0].toInt()
            out = list[1].toString()
            err = list[2].toString()
        } catch (t: Throwable) {
            exit = 1
            out = ""
            err = "Python 运行错误：${t}"
        }

        if (out.isNotEmpty()) _output.tryEmit(OutputChunk(OutputChunk.Stream.STDOUT, out))
        if (err.isNotEmpty()) _output.tryEmit(OutputChunk(OutputChunk.Stream.STDERR, err))
        RunResult(exit, out, err, System.currentTimeMillis() - start)
    }
}
