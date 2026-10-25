package com.terminalide.ui.components

import androidx.compose.foundation.background
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
                "AI 助手",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = aiVm::clearMessages) {
                Icon(Icons.Filled.Delete, contentDescription = "清空对话")
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
                        label = { Text(backend.displayName) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (ready) "● 就绪" else "○ 未就绪",
                color = if (ready) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))

            when (kind) {
                AiBackendKind.DEEPSEEK, AiBackendKind.DEEPSEEK_R1 -> {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = aiVm::updateApiKeyInput,
                        label = { Text("DeepSeek API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = aiVm::saveApiKeyAndProbe,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("探测验证并保存")
                    }
                    if (hasApiKey) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "已保存 Key",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = aiVm::clearApiKey) { Text("清除") }
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
                        "内置 llama.cpp 推理引擎（离线）。首次使用请下载 GGUF 模型。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "推荐模型（hf-mirror 镜像，点选自动填入 URL）：",
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
                        label = { Text("模型下载 URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = aiVm::downloadModel,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("下载模型")
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
                    if (modelPath.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "模型路径：$modelPath",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
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
                MessageBubble(msg, onInsertCode)
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
                placeholder = { Text("输入问题...") },
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
}

@Composable
private fun MessageBubble(msg: AiMessage, onInsertCode: (String) -> Unit) {
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
                    TextButton(onClick = { onInsertCode(block) }) {
                        Text("插入代码")
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
