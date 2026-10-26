package com.terminalide.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.terminalide.R
import com.terminalide.ui.TerminalViewModel

/** 终端面板：上方命令输出，下方单行输入。 */
@Composable
fun TerminalSheet(termVm: TerminalViewModel) {
    val output by termVm.output.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    LaunchedEffect(output) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(Modifier.fillMaxWidth().fillMaxHeight(0.82f)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.terminal_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = { termVm.clear() }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_clear))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

        SelectionContainer(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Text(
                output.ifEmpty { "…" },
                modifier = Modifier.padding(12.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = Color(0xFFB8E986),
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.terminal_input_hint)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    termVm.start()
                    if (input.isNotBlank()) {
                        termVm.send(input)
                        input = ""
                    }
                }),
            )
            IconButton(onClick = {
                termVm.start()
                if (input.isNotBlank()) {
                    termVm.send(input)
                    input = ""
                }
            }) {
                Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.action_send))
            }
        }
    }
}
