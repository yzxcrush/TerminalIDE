package com.terminalide.packages

import com.chaquo.python.Python
import com.terminalide.core.Sandbox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 低层 pip 调用：通过随 APK 打包的 `pip_runner.py` 在嵌入式 CPython 内跑 pip。
 * 所有操作在 IO 线程执行，不卡 UI。
 */
class PipRunner(private val sandbox: Sandbox) {

    suspend fun install(packageName: String, indexUrl: String, extraIndexUrl: String?): PipResult =
        withContext(Dispatchers.IO) {
            call(
                "install",
                packageName,
                sandbox.sitePackages.absolutePath,
                indexUrl,
                extraIndexUrl ?: ""
            )
        }

    suspend fun uninstall(packageName: String): PipResult = withContext(Dispatchers.IO) {
        call("uninstall", packageName, sandbox.sitePackages.absolutePath)
    }

    private fun call(method: String, vararg args: Any): PipResult {
        return try {
            val py = Python.getInstance()
            val mod = py.getModule("pip_runner")
            val res = mod.callAttr(method, *args)
            val list = res.asList()
            PipResult(
                exitCode = list[0].toInt(),
                stdout = list[1].toString(),
                stderr = list[2].toString(),
            )
        } catch (t: Throwable) {
            PipResult(1, "", "pip 调用失败：${t}")
        }
    }
}
