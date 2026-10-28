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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.terminalide.ui.ToolsViewModel

/**
 * 脚本工具：工作区里每个顶层函数自动变成一个可填表运行的小工具。
 */
@Composable
fun ToolsSheet(vm: ToolsViewModel) {
    val tools by vm.tools.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val output by vm.output.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<ToolsViewModel.ToolInfo?>(null) }

    LaunchedEffect(Unit) { vm.refresh() }

    Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
        Text(
            stringResource(R.string.tools_title),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        if (tools.isEmpty()) {
            Text(
                stringResource(R.string.tools_empty),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(tools, key = { it.file + ":" + it.func }) { tool ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable { selected = tool }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tool.func, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "  (${tool.params.size} 参数)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "${tool.file} · ${tool.doc}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // 参数表单
    val cur = selected
    if (cur != null) {
        var values by remember(cur) { mutableStateOf(mapOf<String, String>()) }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("${cur.func}()") },
            text = {
                Column(Modifier.height(360.dp).verticalScroll(rememberScrollState())) {
                    if (cur.doc.isNotBlank()) {
                        Text(cur.doc, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    cur.params.forEach { p ->
                        OutlinedTextField(
                            value = values[p.name] ?: "",
                            onValueChange = { values = values + (p.name to it) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            singleLine = true,
                            label = { Text("${p.name}: ${p.type}", fontSize = 11.sp) },
                            textStyle = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { vm.run(cur, values); selected = null }) {
                    Text(stringResource(R.string.tools_run))
                }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    // 运行结果
    val out = output
    if (out != null) {
        AlertDialog(
            onDismissRequest = { vm.clearOutput() },
            title = { Text(if (running) "运行中…" else "运行结果") },
            text = {
                Column(Modifier.height(400.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(out, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.clearOutput() }) { Text("关闭") }
            },
            dismissButton = {},
        )
    }
}
