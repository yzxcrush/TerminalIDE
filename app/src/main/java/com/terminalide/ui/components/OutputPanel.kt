package com.terminalide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * 运行输出面板（控制台）：
 *  - 输出实时滚动到底部
 *  - 运行中底部有交互输入框（程序 input() 会等这里输入）
 *  - 「标准输入」可预先填多行（非交互场景）
 */
@Composable
fun OutputPanel(
    output: String,
    isRunning: Boolean,
    stdin: String,
    onStdinChange: (String) -> Unit,
    onSendStdin: (String) -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var showStdin by remember { mutableStateOf(false) }
    var consoleInput by remember { mutableStateOf("") }

    // 输出更新后自动滚到底部，保证最新的运行结果始终可见
    LaunchedEffect(output) {
        if (output.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "控制台",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = { showStdin = !showStdin }) {
                Text(if (showStdin) "收起预填" else "预填输入")
            }
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "清空",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isRunning) {
                IconButton(onClick = onStop) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = "停止",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        if (showStdin) {
            OutlinedTextField(
                value = stdin,
                onValueChange = onStdinChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .heightIn(min = 64.dp),
                label = { Text("预填输入（每行一条，运行时也会依次读入）") },
                placeholder = { Text("例如：1") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                minLines = 2,
                maxLines = 4,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (output.isEmpty()) {
                Text(
                    if (isRunning) "程序运行中…输入内容请用下方输入框"
                    else "点击运行以查看输出（交互式程序运行时可直接在下方输入）",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SelectionContainer(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    Text(
                        output,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        // 运行中：交互式输入行（程序 input() 会等待这里的内容）
        if (isRunning) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = consoleInput,
                    onValueChange = { consoleInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("在此输入，回车发送给程序") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
                IconButton(
                    onClick = {
                        if (consoleInput.isNotEmpty()) {
                            onSendStdin(consoleInput)
                            consoleInput = ""
                        }
                    }
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "发送",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
