package com.terminalide.ai

import android.content.Context
import com.terminalide.core.RunResult
import com.terminalide.core.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 本地端侧小模型后端（默认，离线）。
 *
 * 模型与推理运行时都不打包进 APK，运行期可选下载：
 *   - 模型：GGUF 等，下载到 files/models（路径存于 SettingsStore.localModelPath）
 *   - 运行时：如 llama.cpp 的 llama-cli（aarch64），放到 files/models 并赋予执行权限
 *
 * 推理通过子进程执行：`<runner> -m <model> -p "<prompt>" ...`，离线无网络。
 */
class LocalModelBackend(
    context: Context,
    private val settings: SettingsStore,
) : AiBackend {

    override val kind: AiBackendKind = AiBackendKind.LOCAL

    private val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }

    override suspend fun isReady(): Boolean = withContext(Dispatchers.IO) {
        val model = settings.localModelPath
        model.isNotBlank() && File(model).exists() && runnerPath() != null
    }

    override suspend fun chat(history: List<AiMessage>): String = withContext(Dispatchers.IO) {
        val runner = runnerPath()
            ?: throw IllegalStateException("未配置推理运行时（请将 llama-cli 放入 files/models）")
        val model = settings.localModelPath.ifBlank {
            throw IllegalStateException("未下载模型")
        }
        if (!File(model).exists()) throw IllegalStateException("模型文件不存在：$model")

        val prompt = buildPrompt(history)
        val cmd = listOf(
            runner.absolutePath,
            "-m", model,
            "-p", prompt,
            "--temp", "0.3",
            "-n", "512",
            "--no-display",
        )
        val result = runProcess(cmd)
        if (result.exitCode != 0) {
            throw IllegalStateException("本地推理失败：${result.mergedOutput().take(500)}")
        }
        result.stdout.trim()
    }

    override suspend fun probe(): ProbeResult {
        val ready = isReady()
        return if (ready) {
            ProbeResult(true, "本地模型就绪")
        } else {
            ProbeResult(false, "本地模型未配置（未下载模型或未放置推理运行时）")
        }
    }

    private fun runnerPath(): File? =
        listOf("llama-cli", "main")
            .map { File(modelsDir, it) }
            .firstOrNull { it.exists() && it.canExecute() }

    /** 把对话历史拼成 llama.cpp 风格的 prompt（含系统/用户/助手标记）。 */
    private fun buildPrompt(history: List<AiMessage>): String = buildString {
        history.forEach { m ->
            when (m.role) {
                AiMessage.Role.SYSTEM -> append("系统：").append(m.content).append('\n')
                AiMessage.Role.USER -> append("用户：").append(m.content).append('\n')
                AiMessage.Role.ASSISTANT -> append("助手：").append(m.content).append('\n')
            }
        }
        append("助手：")
    }

    private fun runProcess(cmd: List<String>): RunResult {
        val start = System.currentTimeMillis()
        val proc = ProcessBuilder(cmd)
            .directory(modelsDir)
            .redirectErrorStream(false)
            .start()
        val stdout = proc.inputStream.bufferedReader().use { it.readText() }
        val stderr = proc.errorStream.bufferedReader().use { it.readText() }
        val finished = proc.waitFor(180, TimeUnit.SECONDS)
        if (!finished) proc.destroyForcibly()
        val code = if (finished) proc.exitValue() else -1
        return RunResult(code, stdout, stderr, System.currentTimeMillis() - start)
    }
}
