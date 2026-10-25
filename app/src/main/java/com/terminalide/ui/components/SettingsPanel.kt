package com.terminalide.ui.components

import com.terminalide.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terminalide.ai.AiBackendKind
import com.terminalide.packages.MirrorSource
import com.terminalide.ui.AiViewModel
import com.terminalide.ui.PackageViewModel

private val SuccessGreen = Color(0xFF4CAF50)

/**
 * 设置面板：镜像源 + AI 后端 + DeepSeek Key + 端侧模型下载（与 AiSidebar 可独立使用）。
 */
@Composable
fun SettingsPanel(packageVm: PackageViewModel, aiVm: AiViewModel) {
    val mirror by packageVm.mirror.collectAsStateWithLifecycle()
    val packageStatus by packageVm.status.collectAsStateWithLifecycle()
    val kind by aiVm.kind.collectAsStateWithLifecycle()
    val apiKeyInput by aiVm.apiKeyInput.collectAsStateWithLifecycle()
    val probeResult by aiVm.probeResult.collectAsStateWithLifecycle()
    val aiStatus by aiVm.status.collectAsStateWithLifecycle()
    val modelUrl by aiVm.modelUrl.collectAsStateWithLifecycle()
    val downloadProgress by aiVm.downloadProgress.collectAsStateWithLifecycle()
    val modelPath by aiVm.modelPath.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        item { Spacer(Modifier.height(12.dp)) }

        // ---- 语言 / Language ----
        item {
            Text(
                stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            val context = LocalContext.current
            val settingsStore = com.terminalide.TerminalIdeApp.instance.settings
            var currentLang by remember { mutableStateOf(settingsStore.appLanguage) }
            Column {
                listOf(
                    "system" to stringResource(R.string.settings_language_system),
                    "zh" to "简体中文",
                    "en" to "English",
                    "zh-TW" to "繁體中文",
                    "ja" to "日本語",
                ).forEach { (code, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                settingsStore.appLanguage = code
                                currentLang = code
                                context.findActivity()?.recreate()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = currentLang == code, onClick = null)
                        Text(label)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }

        item {
            Text(
                "镜像源",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Column {
                MirrorSource.entries.forEach { src ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { packageVm.setMirror(src) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mirror == src, onClick = null)
                        Column {
                            Text(src.displayName)
                            Text(
                                src.indexUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }

        item {
            Text(
                stringResource(R.string.settings_ai_backend),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Column {
                AiBackendKind.entries.forEach { backend ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { aiVm.setKind(backend) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = kind == backend, onClick = null)
                        Text(backend.displayName)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }

        item {
            Text(
                stringResource(R.string.settings_api_key),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = aiVm::updateApiKeyInput,
                label = { Text(stringResource(R.string.settings_api_key_hint)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Column {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = aiVm::saveApiKeyAndProbe,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_verify_save))
                }
            }
        }
        probeResult?.let { pr ->
            item {
                Text(
                    if (pr.ok) "✅ 通过" else "❌ ${pr.message}",
                    color = if (pr.ok) SuccessGreen else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (aiStatus.isNotEmpty()) {
            item {
                Text(
                    aiStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }

        item {
            Text(
                stringResource(R.string.settings_local_model),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            OutlinedTextField(
                value = modelUrl,
                onValueChange = aiVm::updateModelUrl,
                label = { Text(stringResource(R.string.settings_download_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Column {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = aiVm::downloadModel,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("下载")
                }
            }
        }
        if (downloadProgress.isNotEmpty()) {
            item {
                Text(
                    downloadProgress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (modelPath.isNotEmpty()) {
            item {
                Text(
                    "模型路径：$modelPath",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (packageStatus.isNotEmpty()) {
            item {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        packageStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 从 Context 包装链里找到宿主 Activity（Compose 的 LocalContext 通常不是 Activity 本身）。 */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
