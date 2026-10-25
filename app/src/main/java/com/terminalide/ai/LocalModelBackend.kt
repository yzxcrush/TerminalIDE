package com.terminalide.ai

import android.content.Context
import com.terminalide.core.RunResult
import com.terminalide.core.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 本地端侧小模型后端（离线）。
 *
 * 推理运行时（llama.cpp 的 llama-completion）随 APK 打包在 jniLibs，
 * 安装后位于 nativeLibraryDir（唯一允许执行二进制的目录），运行期直接执行；
 * GGUF 模型运行期下载到 files/models。
 *
 * 采用 llama.cpp 单轮对话模式（-st），由引擎套用模型自带的 chat 模板，
 * 回复质量远好于手拼 prompt。输出会剥离 R1 的 <think>…</think> 推理块。
 */
class LocalModelBackend(
    private val context: Context,
    private val settings: SettingsStore,
) : AiBackend {

    override val kind: AiBackendKind = AiBackendKind.LOCAL

    private val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }
    private val nativeDir: File = File(context.applicationInfo.nativeLibraryDir)

    override suspend fun isReady(): Boolean = withContext(Dispatchers.IO) {
        val model = settings.localModelPath
        model.isNotBlank() && File(model).exists() && runnerPath() != null
    }

    override suspend fun chat(history: List<AiMessage>): String = withContext(Dispatchers.IO) {
        val runner = runnerPath()
            ?: throw IllegalStateException("未找到内置推理引擎（llama-completion）")
        val model = settings.localModelPath.ifBlank {
            throw IllegalStateException("未下载模型")
        }
        if (!File(model).exists()) throw IllegalStateException("模型文件不存在：$model")

        val system = history.firstOrNull { it.role == AiMessage.Role.SYSTEM }?.content.orEmpty()
        val userPrompt = buildUserPrompt(history)
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)

        val cmd = mutableListOf(runner.absolutePath, "-m", model)
        if (system.isNotBlank()) {
            cmd.add("-sys")
            cmd.add(system)
        }
        cmd.addAll(
            listOf(
                "-st",
                "--no-display-prompt",
                "-p", userPrompt,
                "-n", "1024",
                "--temp", "0.3",
                "-c", "4096",
                "-t", threads.toString(),
            )
        )
        val result = runProcess(cmd)
        if (result.exitCode != 0) {
            throw IllegalStateException("本地推理失败：${result.mergedOutput().take(500)}")
        }
        cleanReply(result.stdout)
    }

    override suspend fun probe(): ProbeResult {
        val ready = isReady()
        return if (ready) {
            ProbeResult(true, "本地模型就绪")
        } else {
            ProbeResult(false, "本地模型未配置（未下载模型）")
        }
    }

    /** 内置推理引擎：jniLibs 中伪装成 .so 的可执行文件，位于原生库目录。 */
    private fun runnerPath(): File? =
        listOf("libllama_completion.so", "libllama_cli.so")
            .map { File(nativeDir, it) }
            .firstOrNull { it.exists() && it.canExecute() }

    /** 把除系统消息外的对话历史拼成一段供 -p 使用的文本。 */
    private fun buildUserPrompt(history: List<AiMessage>): String = buildString {
        history.filter { it.role != AiMessage.Role.SYSTEM }.forEach { m ->
            append(if (m.role == AiMessage.Role.USER) "用户：" else "助手：")
            append(m.content).append('\n')
        }
        append("助手：")
    }

    /** 去掉 R1 的 <think>…</think> 推理块，只保留最终回答。 */
    private fun cleanReply(raw: String): String {
        val noThink = raw.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
        return noThink.ifBlank { raw.trim() }
    }

    private fun runProcess(cmd: List<String>): RunResult {
        val start = System.currentTimeMillis()
        val pb = ProcessBuilder(cmd)
        pb.directory(modelsDir)
        pb.environment()["LD_LIBRARY_PATH"] = nativeDir.absolutePath
        val proc = pb.redirectErrorStream(false).start()
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val tOut = Thread { proc.inputStream.bufferedReader().forEachLine { stdout.append(it).append('\n') } }.apply { start() }
        val tErr = Thread { proc.errorStream.bufferedReader().forEachLine { stderr.append(it).append('\n') } }.apply { start() }
        val finished = proc.waitFor(300, TimeUnit.SECONDS)
        if (!finished) proc.destroyForcibly()
        tOut.join(2000)
        tErr.join(2000)
        val code = if (finished) proc.exitValue() else -1
        return RunResult(code, stdout.toString(), stderr.toString(), System.currentTimeMillis() - start)
    }
}
