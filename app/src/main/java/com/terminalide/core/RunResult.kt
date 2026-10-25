package com.terminalide.core

/**
 * 一次运行（Python 解释执行 / C、C++ 编译并执行）的统一结果。
 */
data class RunResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
) {
    val isSuccess: Boolean get() = exitCode == 0

    /** stdout 与 stderr 合并，供终端面板整体展示。 */
    fun mergedOutput(): String = buildString {
        if (stdout.isNotBlank()) append(stdout.trimEnd())
        if (stderr.isNotBlank()) {
            if (stdout.isNotBlank()) append('\n')
            append(stderr.trimEnd())
        }
    }
}
