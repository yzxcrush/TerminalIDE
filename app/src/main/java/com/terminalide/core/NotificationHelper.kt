package com.terminalide.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 运行完成通知：长脚本跑完/出错时在通知栏提醒（可以切出去干别的）。
 */
object NotificationHelper {

    private const val CHANNEL_ID = "run_done"
    private const val NOTIFICATION_ID = 1001

    /** 至少运行这么久（毫秒）才发通知，避免几百毫秒的脚本也弹通知。 */
    const val MIN_DURATION_MS = 3000L

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "运行完成",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "脚本运行结束后的提醒"
        }
        mgr.createNotificationChannel(channel)
    }

    /** 发送「运行完成」通知。 */
    fun notifyRunDone(context: Context, exitCode: Int, durationMs: Long) {
        if (!hasPermission(context)) return
        ensureChannel(context)

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        val pending = PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val ok = exitCode == 0
        val text = if (ok) "运行成功 · 耗时 ${durationMs} ms"
        else "运行结束（退出码 $exitCode）· 耗时 ${durationMs} ms"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(if (ok) "✅ 脚本运行完成" else "⚠️ 脚本运行结束")
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    /** 供 MainActivity 请求权限（API 33+）。 */
    fun requestPermissionIntent(): Intent? = null
}
