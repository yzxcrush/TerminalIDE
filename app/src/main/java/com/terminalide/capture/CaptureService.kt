package com.terminalide.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.terminalide.MainActivity
import com.terminalide.R

/**
 * 抓包前台服务：
 *  - 代理运行在这个服务里，前台服务让系统不容易杀进程
 *  - 服务被销毁时**自动清除系统代理设置**，避免断网
 *  - 通知栏可一键停止
 */
class CaptureService : Service() {

    /** 抓包期间持有 CPU 唤醒锁，避免进程被冻结导致代理「假死」。 */
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        runCatching {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "TerminalIDE::CaptureProxy",
            ).apply {
                setReferenceCounted(false)
                acquire(2 * 60 * 60 * 1000L) // 最长 2 小时，防止忘记释放
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }


    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        acquireWakeLock()
        val ok = CaptureEngine.start(this)
        try {
            startForeground(NOTIF_ID, buildNotification(ok))
        } catch (_: Throwable) {
            // 前台服务启动失败也不能崩，退回普通服务
        }
        return START_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        CaptureEngine.stop()
        // 关键兜底：服务结束就把系统代理清掉（放子线程，避免阻塞主线程）
        Thread {
            runCatching {
                ProcessBuilder("su", "-c", "settings put global http_proxy :0")
                    .start().waitFor()
            }
        }.apply { isDaemon = true }.start()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.capture_title),
                NotificationManager.IMPORTANCE_LOW,
            )
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(ok: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(getString(R.string.capture_title))
            .setContentText(
                if (ok) "抓包中 127.0.0.1:${CaptureEngine.PORT}"
                else "启动失败：端口 ${CaptureEngine.PORT} 被占用"
            )
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.action_stop), stopIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "capture_service"
        private const val NOTIF_ID = 0x0CA9
        private const val ACTION_STOP = "com.terminalide.capture.STOP"

        fun start(ctx: Context) {
            runCatching {
                val i = Intent(ctx, CaptureService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(i)
                } else {
                    ctx.startService(i)
                }
            }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, CaptureService::class.java)) }
        }
    }
}
