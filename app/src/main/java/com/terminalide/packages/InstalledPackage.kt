package com.terminalide.packages

/**
 * 沙箱 site-packages 中已安装的包。
 */
data class InstalledPackage(
    val name: String,
    val version: String,
    val location: String? = null,
)

/**
 * 包查询（搜索）结果元数据。
 */
data class PackageInfo(
    val name: String,
    val version: String,
    val summary: String,
)

/**
 * 一次 pip 操作的结果。
 */
data class PipResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val isSuccess: Boolean get() = exitCode == 0

    fun message(): String = buildString {
        if (stdout.isNotBlank()) append(stdout.trimEnd())
        if (stderr.isNotBlank()) {
            if (stdout.isNotBlank()) append('\n')
            append(stderr.trimEnd())
        }
    }
}
