package com.terminalide.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自愈：如果系统代理还指着抓包端口但抓包服务没在跑，自动清掉，防止断网。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // 开机后恢复自动化任务的闹钟
        runCatching { com.terminalide.automation.AutomationScheduler.rescheduleAll(context) }

        // 开机后自动把 CA 注入系统证书库（不依赖 KernelSU 模块）
        runCatching {
            val app = com.terminalide.TerminalIdeApp.instance
            val ca = com.terminalide.capture.CaManager(app)
            ca.ensure()
            if (!CaInstaller.isInstalled(app, ca)) {
                CaInstaller.install(app, ca)
            }
        }

        Thread {
            runCatching {
                val out = ProcessBuilder("su", "-c", "settings get global http_proxy")
                    .start().apply { waitFor() }.inputStream.bufferedReader().readText().trim()
                if (out.contains("8888") && !CaptureEngine.isRunning()) {
                    ProcessBuilder("su", "-c", "settings delete global http_proxy")
                        .start().waitFor()
                }
            }
        }.apply { isDaemon = true }.start()
    }
}
