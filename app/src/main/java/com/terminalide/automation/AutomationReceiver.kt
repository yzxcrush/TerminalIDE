package com.terminalide.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.terminalide.R
import com.terminalide.TerminalIdeApp
import com.terminalide.backend.PythonBackend
import com.terminalide.core.Language
import kotlinx.coroutines.runBlocking

/** 自动化任务触发：后台运行脚本，并把结果发通知。 */
class AutomationReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val task = AutomationStore.find(ctx, id) ?: return
        if (!task.enabled) return

        val pending = goAsync()
        Thread {
            val output = runCatching {
                val app = TerminalIdeApp.instance
                val backend = app.backends.backendFor(Language.PYTHON) as PythonBackend
                runBlocking { backend.runScript(task.script) }
            }.getOrElse { "执行失败：${it.message}" }
            notifyResult(ctx, task, output)
            pending.finish()
        }.start()
    }

    private fun notifyResult(ctx: Context, task: AutomationTask, output: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel("automation", "自动化任务", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val n = android.app.Notification.Builder(ctx, "automation")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("自动化：${task.name}")
            .setContentText(output.replace("\n", " ").take(120))
            .setStyle(android.app.Notification.BigTextStyle().bigText(output.take(2000)))
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(task.id.hashCode(), n) }
    }
}
