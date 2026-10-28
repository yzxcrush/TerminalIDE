package com.terminalide.automation

import android.content.Context
import com.terminalide.TerminalIdeApp
import org.json.JSONArray
import org.json.JSONObject

/** 一条自动化任务：每隔 N 分钟运行某个脚本。 */
data class AutomationTask(
    val id: String,
    val name: String,
    val script: String,
    val intervalMinutes: Int,
    val enabled: Boolean,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("script", script)
        put("interval", intervalMinutes)
        put("enabled", enabled)
    }

    companion object {
        fun fromJson(o: JSONObject) = AutomationTask(
            id = o.optString("id"),
            name = o.optString("name"),
            script = o.optString("script"),
            intervalMinutes = o.optInt("interval", 60),
            enabled = o.optBoolean("enabled", true),
        )
    }
}

/** 任务持久化（SharedPreferences JSON）。 */
object AutomationStore {
    private const val KEY = "automation_tasks"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("terminal_ide_settings", Context.MODE_PRIVATE)

    fun load(ctx: Context): List<AutomationTask> = runCatching {
        val raw = prefs(ctx).getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(raw)
        (0 until arr.length()).map { AutomationTask.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun save(ctx: Context, tasks: List<AutomationTask>) {
        val arr = JSONArray()
        tasks.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    fun find(ctx: Context, id: String): AutomationTask? =
        load(ctx).firstOrNull { it.id == id }
}
