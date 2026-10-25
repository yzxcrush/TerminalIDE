package com.terminalide.packages

import android.content.Context
import com.terminalide.core.Sandbox
import com.terminalide.core.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * 包管理器：镜像源切换 + 搜索/安装/卸载/已装列表/导出 requirements.txt。
 *
 * 镜像源两种模式：
 *  - 全局默认源：通过 [setGlobalMirror] 持久化，[install] 不传 mirror 时使用。
 *  - 临时指定源：[install] 传入 mirror，仅本次生效，不改全局设置。
 */
class PackageManager(
    context: Context,
    private val sandbox: Sandbox,
    private val settings: SettingsStore,
) {

    private val pip = PipRunner(sandbox)
    private val http = OkHttpClient()

    // ---- 镜像源切换 ----

    fun globalMirror(): MirrorSource = MirrorSource.byId(settings.globalMirrorId)

    fun setGlobalMirror(source: MirrorSource) {
        settings.globalMirrorId = source.id
    }

    // ---- 安装 / 卸载 ----

    /** 安装包；[mirror] 为 null 时使用全局默认源。原生包自动走 Chaquopy 预编译 wheel 仓库。 */
    suspend fun install(packageName: String, mirror: MirrorSource? = null): PipResult {
        val source = mirror ?: globalMirror()
        return pip.install(packageName, source.indexUrl, MirrorSource.CHAQUOPY_REPO)
    }

    suspend fun uninstall(packageName: String): PipResult = pip.uninstall(packageName)

    // ---- 已装列表 / 导出 ----

    /** 已装列表：扫描沙箱 site-packages 的 dist-info / egg-info。 */
    suspend fun installed(): List<InstalledPackage> = withContext(Dispatchers.IO) {
        val dir = sandbox.sitePackages
        if (!dir.exists()) return@withContext emptyList()
        dir.listFiles()
            ?.asSequence()
            ?.filter { it.name.endsWith(".dist-info") || it.name.endsWith(".egg-info") }
            ?.mapNotNull { readDistInfo(it) }
            ?.sortedBy { it.name.lowercase() }
            ?.toList()
            ?: emptyList()
    }

    /** 导出 requirements.txt 内容（name==version 每行一条）。 */
    suspend fun exportRequirements(): String =
        installed().joinToString("\n") { "${it.name}==${it.version}" } + "\n"

    // ---- 搜索（按包名精确查询 pypi.org JSON API） ----

    suspend fun search(name: String): PackageInfo? = withContext(Dispatchers.IO) {
        val query = name.trim()
        if (query.isEmpty()) return@withContext null
        val url = "https://pypi.org/pypi/${query}/json"
        try {
            val req = Request.Builder().url(url).get().build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val info = json.getJSONObject("info")
                PackageInfo(
                    name = info.optString("name").ifEmpty { query },
                    version = info.optString("version").ifEmpty { "unknown" },
                    summary = info.optString("summary").ifEmpty { "（无描述）" },
                )
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun readDistInfo(dir: File): InstalledPackage? {
        val meta = File(dir, "METADATA").takeIf { it.exists() }
            ?: File(dir, "PKG-INFO").takeIf { it.exists() }
            ?: return null

        var name: String? = null
        var version: String? = null
        meta.forEachLine { line ->
            if (name == null && line.startsWith("Name:")) name = line.substringAfter(":").trim()
            if (version == null && line.startsWith("Version:")) version = line.substringAfter(":").trim()
        }
        val n = name ?: dir.name.substringBeforeLast("-")
        return InstalledPackage(n, version ?: "?", dir.absolutePath)
    }
}
