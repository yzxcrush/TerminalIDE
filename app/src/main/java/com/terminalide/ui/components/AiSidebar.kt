package com.terminalide.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.ui.platform.LocalContext
import com.terminalide.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terminalide.ai.AiBackendKind
import com.terminalide.ai.AiMessage
import com.terminalide.core.Language
import com.terminalide.ui.AiViewModel

/** hf-mirror 镜像上的 DeepSeek-R1 蒸馏模型（GGUF Q4_K_M）。 */
private const val MODEL_R1_7B =
    "https://hf-mirror.com/unsloth/DeepSeek-R1-Distill-Qwen-7B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-7B-Q4_K_M.gguf"
private const val MODEL_R1_15B =
    "https://hf-mirror.com/unsloth/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf"

private val SuccessGreen = Color(0xFF4CAF50)

/**
 * 右侧 AI 侧边栏：后端切换 + 就绪状态 + DeepSeek/端侧配置 + 对话区 + 输入区。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSidebar(
    aiVm: AiViewModel,
    currentLanguage: Language,
    currentCode: String,
    onInsertCode: (String) -> Unit,
    onApplyAndRun: (String) -> Unit,
) {
    val kind by aiVm.kind.collectAsStateWithLifecycle()
    val messages by aiVm.messages.collectAsStateWithLifecycle()
    val input by aiVm.input.collectAsStateWithLifecycle()
    val isThinking by aiVm.isThinking.collectAsStateWithLifecycle()
    val ready by aiVm.ready.collectAsStateWithLifecycle()
    val status by aiVm.status.collectAsStateWithLifecycle()
    val apiKeyInput by aiVm.apiKeyInput.collectAsStateWithLifecycle()
    val hasApiKey by aiVm.hasApiKey.collectAsStateWithLifecycle()
    val probeResult by aiVm.probeResult.collectAsStateWithLifecycle()
    val modelUrl by aiVm.modelUrl.collectAsStateWithLifecycle()
    val downloadProgress by aiVm.downloadProgress.collectAsStateWithLifecycle()
    val modelPath by aiVm.modelPath.collectAsStateWithLifecycle()

    // 本地模型管理区默认收起，避免占满面板、挤压聊天区
    var showModelMgmt by remember { mutableStateOf(false) }
    var showGenerate by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 标题
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.ai_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = aiVm::clearMessages) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.ai_clear_chat))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 后端切换
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiBackendKind.entries.forEach { backend ->
                    FilterChip(
                        selected = kind == backend,
                        onClick = { aiVm.setKind(backend) },
                        label = {
                            Text(
                                stringResource(
                                    when (backend) {
                                        AiBackendKind.LOCAL -> R.string.ai_backend_local
                                        AiBackendKind.DEEPSEEK -> R.string.ai_backend_chat
                                        AiBackendKind.DEEPSEEK_R1 -> R.string.ai_backend_r1
                                    }
                                )
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (ready) stringResource(R.string.ai_ready) else stringResource(R.string.ai_not_ready),
                color = if (ready) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))

            when (kind) {
                AiBackendKind.DEEPSEEK, AiBackendKind.DEEPSEEK_R1 -> {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = aiVm::updateApiKeyInput,
                        label = { Text(stringResource(R.string.ai_api_key_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = aiVm::saveApiKeyAndProbe,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.ai_api_key_verify))
                    }
                    if (hasApiKey) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.ai_api_key_saved),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = aiVm::clearApiKey) { Text(stringResource(R.string.ai_clear)) }
                        }
                    }
                    probeResult?.let { pr ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (pr.ok) "✅ 通过" else "❌ ${pr.message}",
                            color = if (pr.ok) SuccessGreen else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                AiBackendKind.LOCAL -> {
                    Text(
                        if (modelPath.isNotEmpty())
                            stringResource(R.string.ai_model_prefix) + modelPath.substringAfterLast('/')
                        else stringResource(R.string.ai_no_model),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { showModelMgmt = !showModelMgmt }) {
                        Text(stringResource(if (showModelMgmt) R.string.ai_model_manage_collapse else R.string.ai_model_manage))
                    }
                    if (showModelMgmt) {
                        Text(
                            stringResource(R.string.ai_recommend_models),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { aiVm.updateModelUrl(MODEL_R1_7B) },
                                modifier = Modifier.weight(1f),
                            ) { Text("R1-7B 4.7G", fontSize = 12.sp) }
                            OutlinedButton(
                                onClick = { aiVm.updateModelUrl(MODEL_R1_15B) },
                                modifier = Modifier.weight(1f),
                            ) { Text("R1-1.5B 1.1G", fontSize = 12.sp) }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = modelUrl,
                            onValueChange = aiVm::updateModelUrl,
                            label = { Text(stringResource(R.string.settings_download_url)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = aiVm::downloadModel,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.ai_download_model))
                        }
                        if (downloadProgress.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    downloadProgress,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        // 对话区
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { msg ->
                MessageBubble(msg, onInsertCode, onApplyAndRun)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        // AI 快捷操作：一键把当前代码交给模型处理
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val ctx = LocalContext.current
            OutlinedButton(
                onClick = { showGenerate = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp),
                enabled = ready && !isThinking,
            ) {
                Text("✨ " + stringResource(R.string.ai_generate), fontSize = 11.sp)
            }
            listOf(
                R.string.ai_quick_explain to "请解释当前编辑器里这段代码的作用，逐段说明，尽量简洁。",
                R.string.ai_quick_fix to "请检查当前编辑器里这段代码的错误，并给出修复后的完整代码。",
                R.string.ai_quick_optimize to "请优化当前编辑器里这段代码，提升可读性和性能，给出完整代码。",
                R.string.ai_quick_comment to "请给当前编辑器里这段代码加上清晰的中文注释，输出完整代码。",
                R.string.ai_quick_test to "请为当前编辑器里这段代码生成测试用例。",
            ).forEach { (labelRes, prompt) ->
                val label = ctx.getString(labelRes)
                OutlinedButton(
                    onClick = { aiVm.sendQuick(label, prompt, currentLanguage, currentCode) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp),
                    enabled = ready && !isThinking,
                ) {
                    Text(label, fontSize = 11.sp)
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        // 输入区
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = aiVm::updateInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.ai_input_hint)) },
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            if (isThinking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(onClick = { aiVm.send(currentLanguage, currentCode) }) {
                    Icon(Icons.Filled.Send, contentDescription = "发送")
                }
            }
        }
        if (status.isNotEmpty()) {
            Text(
                status,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    // AI 生成程序对话框
    if (showGenerate) {
        var genPrompt by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showGenerate = false },
            title = { Text(stringResource(R.string.ai_generate)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.ai_generate_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = genPrompt,
                        onValueChange = { genPrompt = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如：批量给图片加水印") },
                        minLines = 2,
                        maxLines = 4,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (genPrompt.isNotBlank()) {
                        aiVm.sendQuick(
                            "生成程序",
                            "请生成一个完整、可直接运行的 Python 程序：${genPrompt}。只输出一个 Python 代码块，不要多余解释。",
                            currentLanguage,
                            currentCode,
                        )
                    }
                    showGenerate = false
                }) { Text(stringResource(R.string.action_generate)) }
            },
            dismissButton = {
                TextButton(onClick = { showGenerate = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun MessageBubble(
    msg: AiMessage,
    onInsertCode: (String) -> Unit,
    onApplyAndRun: (String) -> Unit,
) {
    val isUser = msg.role == AiMessage.Role.USER
    val bubbleColor =
        if (isUser) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant
    val textColor =
        if (isUser) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 280.dp)
                .background(bubbleColor, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                msg.content,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!isUser) {
                extractCodeBlock(msg.content)?.let { block ->
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.layout.Row {
                        TextButton(onClick = { onInsertCode(block) }) {
                            Text(stringResource(R.string.ai_insert_code))
                        }
                        TextButton(onClick = { onApplyAndRun(block) }) {
                            Text(stringResource(R.string.ai_apply_run))
                        }
                    }
                }
            }
        }
    }
}

private val CODE_BLOCK_REGEX = Regex("```[^`]*?```")

private fun extractCodeBlock(text: String): String? {
    val match = CODE_BLOCK_REGEX.find(text) ?: return null
    val raw = match.value.removePrefix("```").removeSuffix("```")
    val body = raw.substringAfter('\n', raw).trim()
    return body.ifEmpty { null }
}
