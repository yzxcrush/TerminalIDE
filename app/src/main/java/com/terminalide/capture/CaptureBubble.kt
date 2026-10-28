package com.terminalide.capture

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.terminalide.MainActivity
import kotlin.math.abs
import kotlin.math.min

/**
 * 抓包悬浮胶囊：显示实时抓包数量，可拖动、点击打开 App、长按停止抓包。
 *
 * 悬浮窗让 App 保持「可见」，避免被系统在后台限制网络/CPU。
 */
class CaptureBubble(private val ctx: Context) {

    private val wm: WindowManager = ctx.getSystemService(WindowManager::class.java)
    private var view: BubbleView? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    val isShowing: Boolean get() = view != null

    fun show() {
        if (view != null) return
        if (!Settings.canDrawOverlays(ctx)) return

        val v = BubbleView(ctx)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ctx.resources.displayMetrics.widthPixels - 160
            y = ctx.resources.displayMetrics.heightPixels / 3
        }
        runCatching {
            wm.addView(v, lp)
            view = v
            params = lp
            startTicker()
        }
    }

    fun hide() {
        stopTicker()
        runCatching { view?.let { wm.removeView(it) } }
        view = null
        params = null
    }

    /** 每秒刷新抓包数量。 */
    private fun startTicker() {
        handler.post(object : Runnable {
            override fun run() {
                view?.count = CaptureStore.items.value.size
                view?.invalidate()
                if (view != null) handler.postDelayed(this, 1000)
            }
        })
    }

    private fun stopTicker() {
        handler.removeCallbacksAndMessages(null)
    }

    // ---------------- 气泡视图 ----------------

    @SuppressLint("ViewConstructor")
    private inner class BubbleView(context: Context) : View(context) {

        var count: Int = 0
            set(value) { field = value; invalidate() }

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f * resources.displayMetrics.scaledDensity
            isFakeBoldText = true
        }
        private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 200
            textSize = 10f * resources.displayMetrics.scaledDensity
        }
        private val rect = RectF()
        private var pulsePhase = 0f
        private val pulseAnim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener { pulsePhase = it.animatedValue as Float; invalidate() }
        }

        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longPressed = false

        private val widthPx = (128 * resources.displayMetrics.density).toInt()
        private val heightPx = (48 * resources.displayMetrics.density).toInt()

        init {
            bgPaint.shader = LinearGradient(
                0f, 0f, widthPx.toFloat(), heightPx.toFloat(),
                intArrayOf(0xFF4FC3F7.toInt(), 0xFF1E88E5.toInt(), 0xFF6A5AE0.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP,
            )
            bgPaint.setShadowLayer(8f * resources.displayMetrics.density, 0f, 3f, 0x55000000)
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            pulseAnim.start()
        }

        private val longPressRunnable = Runnable {
            longPressed = true
            stopCapture(fromBubble = true)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(widthPx, heightPx)
        }

        override fun onDraw(canvas: Canvas) {
            rect.set(4f, 4f, width - 4f, height - 4f)
            val r = (height - 8f) / 2f
            canvas.drawRoundRect(rect, r, r, bgPaint)

            // 脉冲光点
            val cx = 22f * resources.displayMetrics.density
            val cy = height / 2f
            val base = 5f * resources.displayMetrics.density
            dotPaint.alpha = 255
            canvas.drawCircle(cx, cy, base, dotPaint)
            dotPaint.alpha = (90 * (1f - pulsePhase)).toInt()
            canvas.drawCircle(cx, cy, base * (1.8f + pulsePhase * 1.6f), dotPaint)
            dotPaint.alpha = 255

            // 文案
            val tx = cx + 20f * resources.displayMetrics.density
            canvas.drawText("抓包中", tx, cy - 2f, textPaint)
            canvas.drawText("$count 条", tx, cy + 14f * resources.displayMetrics.density, subPaint)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val lp = params ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    longPressed = false
                    handler.postDelayed(longPressRunnable, 600)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > 12 || abs(dy) > 12)) {
                        dragging = true
                        handler.removeCallbacks(longPressRunnable)
                    }
                    if (dragging) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(this, lp) }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (!dragging && !longPressed) {
                        // 轻点 → 打开 App
                        val i = Intent(ctx, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        runCatching { ctx.startActivity(i) }
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        private fun stopCapture(fromBubble: Boolean) {
            runCatching {
                ctx.stopService(Intent(ctx, CaptureService::class.java))
                Toast.makeText(ctx, "已停止抓包", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        /** 是否有悬浮窗权限。 */
        fun canOverlay(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

        /** 跳转到悬浮窗权限设置。 */
        fun requestPermission(ctx: Context) {
            runCatching {
                ctx.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${ctx.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}
