package com.terminalide.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** 用 AlarmManager 调度自动化任务。 */
object AutomationScheduler {

    private const val RC = 4200

    private fun pendingIntent(ctx: Context, task: AutomationTask): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            task.id.hashCode(),
            Intent(ctx, AutomationReceiver::class.java).putExtra("id", task.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun schedule(ctx: Context, task: AutomationTask) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(ctx, task)
        am.cancel(pi)
        if (!task.enabled) return
        val interval = task.intervalMinutes.coerceAtLeast(1) * 60_000L
        runCatching {
            am.setRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval,
                interval,
                pi,
            )
        }
    }

    fun cancel(ctx: Context, task: AutomationTask) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendingIntent(ctx, task))
    }

    /** 重新调度全部（开机后调用）。 */
    fun rescheduleAll(ctx: Context) {
        AutomationStore.load(ctx).forEach { schedule(ctx, it) }
    }
}
