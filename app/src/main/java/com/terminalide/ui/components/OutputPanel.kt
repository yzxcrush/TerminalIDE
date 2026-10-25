package com.terminalide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * 运行输出面板：可选中的滚动等宽文本 + 清空/停止。
 */
@Composable
fun OutputPanel(
    output: String,
    isRunning: Boolean,
    onStop: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

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
                "输出",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
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

        Box(Modifier.fillMaxSize()) {
            if (output.isEmpty()) {
                Text(
                    "点击运行以查看输出",
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
