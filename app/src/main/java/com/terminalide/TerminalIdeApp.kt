package com.terminalide

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.terminalide.ai.AiService
import com.terminalide.backend.BackendRegistry
import com.terminalide.backend.NdkToolchain
import com.terminalide.core.AssetInstaller
import com.terminalide.core.Sandbox
import com.terminalide.core.SettingsStore
import com.terminalide.packages.PackageManager
import java.io.File

/**
 * 应用入口：启动嵌入式 CPython，并初始化各单例服务。
 */
class TerminalIdeApp : Application() {

    lateinit var sandbox: Sandbox
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var toolchain: NdkToolchain
        private set
    lateinit var packageManager: PackageManager
        private set
    lateinit var backends: BackendRegistry
        private set
    lateinit var aiService: AiService
        private set

    /** 嵌入式 CPython 是否成功启动。 */
    var pythonStarted: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        installCrashHandler()
        startupError = null

        // 【诊断 v1.4】临时跳过 Python，确认闪退是否来自 Python 原生层（16KB 页）。
        pythonStarted = false
        try {
            android.widget.Toast.makeText(this, "诊断：已跳过 Python", android.widget.Toast.LENGTH_LONG).show()
        } catch (_: Throwable) {
        }

        // 逐个初始化服务，任何异常记录到 startupError 并在界面上显示，而不是闪退
        try {
            sandbox = Sandbox(this)
            settings = SettingsStore(this)
            toolchain = NdkToolchain(sandbox)
            packageManager = PackageManager(this, sandbox, settings)
            backends = BackendRegistry(sandbox, toolchain)
            aiService = AiService(this, settings)
            installToolchain()
        } catch (t: Throwable) {
            startupError = "服务初始化失败: ${t}\n${t.stackTraceToString().take(2000)}"
        }
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = getExternalFilesDir(null) ?: filesDir
                val logFile = File(dir, "crash.log")
                val sb = StringBuilder()
                sb.append("time=").append(System.currentTimeMillis()).append('\n')
                sb.append("thread=").append(thread?.name).append('\n')
                sb.append(android.util.Log.getStackTraceString(throwable)).append('\n')
                logFile.writeText(sb.toString())
            } catch (_: Throwable) {
            }
            // 弹 Toast 显示崩溃原因，并延迟崩溃，让用户能看到
            try {
                val main = android.os.Handler(android.os.Looper.getMainLooper())
                val msg = "崩溃: ${throwable.javaClass.simpleName}: ${throwable.message}"
                main.post { android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show() }
                main.postDelayed({ defaultHandler?.uncaughtException(thread, throwable) }, 8000)
            } catch (_: Throwable) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun installToolchain() {
        val binDir = File(sandbox.toolchain, "bin")
        if (binDir.exists() && !binDir.listFiles().isNullOrEmpty()) return
        try {
            AssetInstaller.install(this, "toolchain", sandbox.toolchain)
        } catch (t: Throwable) {
            // 工具链未打包时忽略
        }
        binDir.listFiles()?.forEach { it.setExecutable(true, false) }
    }

    companion object {
        lateinit var instance: TerminalIdeApp
            private set

        /** 启动阶段异常（用于界面显示，而非闪退）。 */
        var startupError: String? = null
            private set
    }
}
