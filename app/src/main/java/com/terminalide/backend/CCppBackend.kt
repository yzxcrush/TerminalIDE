package com.terminalide.backend

import com.terminalide.core.Language
import com.terminalide.core.RunResult
import com.terminalide.core.Sandbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * C/C++ 后端基类：源码写沙箱 → NDK clang/clang++ 编译成静态可执行文件 → 运行 → 实时捕获输出。
 */
abstract class CCppBackend(
    private val sandbox: Sandbox,
    private val toolchain: NdkToolchain,
    private val kind: NdkToolchain.Kind,
) : LanguageBackend {

    protected val _output = MutableSharedFlow<OutputChunk>(extraBufferCapacity = 2048)
    override val output: SharedFlow<OutputChunk> = _output

    override fun reset() = Unit

    override suspend fun run(code: String, stdin: String): RunResult = withContext(Dispatchers.IO) {
        val src = File(sandbox.build, "main.${kind.sourceExt}")
        val bin = File(sandbox.build, "main.bin")
        src.writeText(code)

        val compiled = toolchain.compile(src, bin, kind)
        if (compiled.exitCode != 0) {
            val msg = "[编译失败] exit=${compiled.exitCode}\n${compiled.mergedOutput()}"
            _output.tryEmit(OutputChunk(OutputChunk.Stream.STDERR, msg))
            return@withContext RunResult(compiled.exitCode, "", msg, compiled.durationMs)
        }

        toolchain.run(bin, stdin) { chunk -> _output.tryEmit(chunk) }
    }
}

class CBackend(sandbox: Sandbox, toolchain: NdkToolchain) :
    CCppBackend(sandbox, toolchain, NdkToolchain.Kind.C) {
    override val language: Language = Language.C
}

class CppBackend(sandbox: Sandbox, toolchain: NdkToolchain) :
    CCppBackend(sandbox, toolchain, NdkToolchain.Kind.CPP) {
    override val language: Language = Language.CPP
}
