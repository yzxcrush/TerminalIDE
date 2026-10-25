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

        // 1) 启动嵌入式 CPython（等价于 PyApplication 的做法）
        pythonStarted = try {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(this))
            }
            true
        } catch (t: Throwable) {
            false
        }

        // 2) 初始化沙箱与服务
        sandbox = Sandbox(this)
        settings = SettingsStore(this)
        toolchain = NdkToolchain(sandbox)
        packageManager = PackageManager(this, sandbox, settings)
        backends = BackendRegistry(sandbox, toolchain)
        aiService = AiService(this, settings)

        // 3) 若 APK 打包了 assets/toolchain，首次运行解压到 files/toolchain 并赋予执行权限
        installToolchain()
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
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun installToolchain() {
        val binDir = File(sandbox.toolchain, "bin")
        if (binDir.exists() && !binDir.listFiles().isNullOrEmpty()) return
        try {
            AssetInstaller.install(this, "toolchain", sandbox.toolchain)
        } catch (t: Throwable) {
            // 工具链未打包时忽略（可通过 scripts/fetch_toolchain.sh 获取或运行期下载）
        }
        binDir.listFiles()?.forEach { it.setExecutable(true, false) }
    }

    companion object {
        lateinit var instance: TerminalIdeApp
            private set
    }
}
