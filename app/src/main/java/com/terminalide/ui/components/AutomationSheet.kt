package com.terminalide.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terminalide.R
import com.terminalide.automation.AutomationTask
import com.terminalide.ui.AutomationViewModel

/** 自动化任务列表：定时自动运行脚本。 */
@Composable
fun AutomationSheet(vm: AutomationViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var runTask by remember { mutableStateOf<AutomationTask?>(null) }

    Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.automation_title), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge)
            Button(onClick = { showAdd = true }) { Text(stringResource(R.string.automation_add)) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        if (tasks.isEmpty()) {
            Text(
                stringResource(R.string.automation_empty),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(tasks, key = { it.id }) { task ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(task.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${task.script} · 每 ${task.intervalMinutes} 分钟",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = task.enabled, onCheckedChange = { vm.toggle(task) })
                        }
                        Row {
                            TextButton(onClick = { vm.runNow(task); runTask = task }) {
                                Text(stringResource(R.string.tools_run), fontSize = 11.sp)
                            }
                            TextButton(onClick = { vm.remove(task) }) {
                                Text("删除", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var script by remember { mutableStateOf("") }
        var minutes by remember { mutableStateOf("60") }
        val scripts = remember { vm.scripts() }
        var picker by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.automation_add)) },
            text = {
                Column(Modifier.height(360.dp).verticalScroll(rememberScrollState())) {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("名称", fontSize = 11.sp) })
                    OutlinedTextField(script, { script = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("脚本文件", fontSize = 11.sp) },
                        trailingIcon = {
                            TextButton(onClick = { picker = !picker }) { Text("选择") }
                        })
                    if (picker) {
                        scripts.forEach { s ->
                            Text(
                                s,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { script = s; picker = false }
                                    .padding(vertical = 6.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    OutlinedTextField(minutes, { minutes = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("间隔（分钟）", fontSize = 11.sp) })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (script.isNotBlank()) {
                        vm.add(name, script, minutes.toIntOrNull() ?: 60)
                        showAdd = false
                    }
                }) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (runTask != null) {
        AlertDialog(
            onDismissRequest = { vm.clearResult(); runTask = null },
            title = { Text(if (running) "运行中…" else "运行结果") },
            text = {
                Column(Modifier.height(400.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(result ?: "", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.clearResult(); runTask = null }) { Text("关闭") }
            },
            dismissButton = {},
        )
    }
}
