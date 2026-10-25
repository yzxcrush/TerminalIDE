package com.terminalide.backend

import com.terminalide.core.RunResult
import com.terminalide.core.Sandbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * 管理 App 私有目录内的 NDK/LLVM 交叉编译工具链（aarch64-linux-android-clang / clang++）。
 *
 * 工具链目录结构（由 scripts/fetch_toolchain.sh 下载后放进 app/src/main/assets/toolchain，
 * 运行期由 App 解压到 sandbox.toolchain）：
 * ```
 * toolchain/
 * ├── bin/
 * │   ├── aarch64-linux-android24-clang
 * │   ├── aarch64-linux-android24-clang++   （也兼容 clang / clang++ 别名）
 * ├── sysroot/                               （NDK sysroot，可选）
 * ```
 */
class NdkToolchain(private val sandbox: Sandbox) {

    enum class Kind(val sourceExt: String, val stdFlag: String) {
        C("c", "-std=c11"),
        CPP("cpp", "-std=c++17"),
    }

    private val binDir: File get() = File(sandbox.toolchain, "bin")

    val clangPath: File? get() = resolveCompiler("aarch64-linux-android24-clang", "clang")
    val clangppPath: File? get() = resolveCompiler("aarch64-linux-android24-clang++", "clang++")

    /** 两个编译器是否都已就绪。 */
    val isAvailable: Boolean get() = clangPath != null && clangppPath != null

    /** 工具链缺失时的提示信息。 */
    fun unavailableMessage(): String =
        "未找到 C/C++ 交叉编译器。请运行 scripts/fetch_toolchain.sh 获取工具链，" +
            "或将工具链放入 ${sandbox.toolchain.absolutePath}"

    private fun resolveCompiler(vararg names: String): File? {
        for (name in names) {
            val f = File(binDir, name)
            if (f.exists() && f.canExecute()) return f
        }
        return null
    }

    /**
     * 编译源码为静态可执行文件（-static，不依赖系统动态库）。
     * 返回编译进程的结果；[RunResult.exitCode] == 0 表示编译成功。
     */
    suspend fun compile(source: File, output: File, kind: Kind): RunResult =
        withContext(Dispatchers.IO) {
            val compiler = if (kind == Kind.C) clangPath else clangppPath
            if (compiler == null) {
                return@withContext RunResult(-1, "", unavailableMessage(), 0)
            }

            val cmd = mutableListOf(
                compiler.absolutePath,
                "--target=aarch64-linux-android24",
                kind.stdFlag,
                "-O2",
                "-static",
                "-s",
                source.absolutePath,
                "-o", output.absolutePath,
            )
            val sysroot = File(sandbox.toolchain, "sysroot")
            if (sysroot.isDirectory) {
                cmd += "--sysroot"
                cmd += sysroot.absolutePath
            }
            if (kind == Kind.CPP) cmd += "-static-libstdc++"
            cmd += "-lm"

            runProcess(cmd, sandbox.build, COMPILE_TIMEOUT_SEC)
        }

    /**
     * 运行可执行文件，逐行实时回调 [onOutput]（供 UI 边跑边刷新），
     * 超时则强杀。返回运行结果。
     */
    suspend fun run(bin: File, onOutput: (OutputChunk) -> Unit): RunResult =
        withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val proc = ProcessBuilder(bin.absolutePath)
                .directory(sandbox.build)
                .redirectErrorStream(false)
                .start()

            val stdout = StringBuilder()
            val stderr = StringBuilder()
            val tOut = streamReader(proc.inputStream) { line ->
                stdout.appendLine(line)
                onOutput(OutputChunk(OutputChunk.Stream.STDOUT, line))
            }
            val tErr = streamReader(proc.errorStream) { line ->
                stderr.appendLine(line)
                onOutput(OutputChunk(OutputChunk.Stream.STDERR, line))
            }

            val finished = proc.waitFor(RUN_TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                proc.waitFor(1, TimeUnit.SECONDS)
                stderr.appendLine("[已超时，强制终止]")
            }
            tOut.join()
            tErr.join()

            val code = if (finished) proc.exitValue() else -1
            RunResult(code, stdout.toString(), stderr.toString(), System.currentTimeMillis() - start)
        }

    private fun streamReader(stream: InputStream, onLine: (String) -> Unit): Thread =
        Thread {
            stream.bufferedReader().use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    onLine(line)
                    line = reader.readLine()
                }
            }
        }.apply {
            isDaemon = true
            name = "proc-stream-reader"
            start()
        }

    private fun runProcess(cmd: List<String>, cwd: File, timeoutSec: Long): RunResult {
        val start = System.currentTimeMillis()
        val proc = ProcessBuilder(cmd)
            .directory(cwd)
            .redirectErrorStream(false)
            .start()

        val stdout = proc.inputStream.bufferedReader().use { it.readText() }
        val stderr = proc.errorStream.bufferedReader().use { it.readText() }
        val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) proc.destroyForcibly()

        val code = if (finished) proc.exitValue() else -1
        return RunResult(code, stdout, stderr, System.currentTimeMillis() - start)
    }

    companion object {
        private const val RUN_TIMEOUT_SEC = 30L
        private const val COMPILE_TIMEOUT_SEC = 60L
    }
}
