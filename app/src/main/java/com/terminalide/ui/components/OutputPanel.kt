package com.terminalide.ui.components

import androidx.compose.ui.graphics.Color
import com.terminalide.R
import androidx.compose.ui.res.stringResource
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
 *  - 「预填输入」可预先填多行（非交互场景）
 *  - 交互式输入条在底部导航栏上方（见 ConsoleInputBar）
 */
@Composable
fun OutputPanel(
    output: String,
    isRunning: Boolean,
    stdin: String,
    onStdinChange: (String) -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    hasError: Boolean = false,
    onAnalyzeError: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    var showStdin by remember { mutableStateOf(false) }

    // 输出更新后自动滚到底部，保证最新的运行结果始终可见
    LaunchedEffect(output) {
        if (output.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xFF0F1621).copy(alpha = 0.88f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.console_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (hasError) {
                TextButton(onClick = onAnalyzeError) {
                    Text(
                        stringResource(R.string.console_ai_analyze),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            TextButton(onClick = { showStdin = !showStdin }) {
                Text(stringResource(if (showStdin) R.string.console_prefill_collapse else R.string.console_prefill))
            }
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isRunning) {
                IconButton(onClick = onStop) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.action_stop),
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
                label = { Text(stringResource(R.string.console_prefill_label)) },
                placeholder = { Text(stringResource(R.string.console_prefill_hint)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                minLines = 2,
                maxLines = 4,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (output.isEmpty()) {
                Text(
                    if (isRunning) stringResource(R.string.console_running_hint)
                    else stringResource(R.string.console_empty_hint),
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
    }
}
