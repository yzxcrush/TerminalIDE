package com.terminalide.capture

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 抓包引擎单例：持有代理实例，生命周期跟随 [CaptureService]。
 * 放在前台服务里，避免 App 被系统回收导致「代理死了但系统代理还指着它」。
 */
object CaptureEngine {

    const val PORT = 8888

    private var proxy: CaptureProxy? = null
    private var ca: CaManager? = null

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    @Synchronized
    fun start(ctx: Context): Boolean {
        if (proxy?.isRunning() == true) {
            _running.value = true
            return true
        }
        return runCatching {
            val caMgr = CaManager(ctx.applicationContext)
            caMgr.ensure()
            val p = CaptureProxy(caMgr, PORT)
            val ok = p.start()
            if (ok) {
                ca = caMgr
                proxy = p
                _running.value = true
            }
            ok
        }.getOrDefault(false)
    }

    @Synchronized
    fun stop() {
        runCatching { proxy?.stop() }
        proxy = null
        _running.value = false
    }

    fun isRunning(): Boolean = proxy?.isRunning() == true

    /** 供 UI 导出 CA 使用。 */
    fun caManager(ctx: Context): CaManager = ca ?: CaManager(ctx.applicationContext)
}
