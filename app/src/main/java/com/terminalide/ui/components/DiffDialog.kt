package com.terminalide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalide.R

/** 一行 diff。 */
data class DiffLine(val type: Type, val text: String) {
    enum class Type { SAME, ADD, DEL }
}

/**
 * 简单的行级 diff（LCS）。
 * 超过 [limit] 行时退化为「旧全部删除 + 新全部新增」（避免 O(n²) 内存爆炸）。
 */
fun diffLines(oldText: String, newText: String, limit: Int = 400): List<DiffLine> {
    val a = oldText.lines()
    val b = newText.lines()
    if (a.size > limit || b.size > limit) {
        return a.map { DiffLine(DiffLine.Type.DEL, it) } + b.map { DiffLine(DiffLine.Type.ADD, it) }
    }
    val n = a.size
    val m = b.size
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
        for (j in m - 1 downTo 0) {
            dp[i][j] = if (a[i] == b[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
    }
    val out = mutableListOf<DiffLine>()
    var i = 0
    var j = 0
    while (i < n && j < m) {
        when {
            a[i] == b[j] -> { out.add(DiffLine(DiffLine.Type.SAME, a[i])); i++; j++ }
            dp[i + 1][j] >= dp[i][j + 1] -> { out.add(DiffLine(DiffLine.Type.DEL, a[i])); i++ }
            else -> { out.add(DiffLine(DiffLine.Type.ADD, b[j])); j++ }
        }
    }
    while (i < n) { out.add(DiffLine(DiffLine.Type.DEL, a[i])); i++ }
    while (j < m) { out.add(DiffLine(DiffLine.Type.ADD, b[j])); j++ }
    return out
}

private val AddBg = Color(0x1A4CAF50)
private val DelBg = Color(0x1AF44336)
private val AddFg = Color(0xFF81C784)
private val DelFg = Color(0xFFE57373)

/** AI 修复前后对比对话框。 */
@Composable
fun DiffDialog(
    oldCode: String,
    newCode: String,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val lines = remember(oldCode, newCode) { diffLines(oldCode, newCode) }
    val added = lines.count { it.type == DiffLine.Type.ADD }
    val removed = lines.count { it.type == DiffLine.Type.DEL }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diff_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                Text(
                    "+$added  −$removed",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(lines) { line ->
                        val isAdd = line.type == DiffLine.Type.ADD
                        val isDel = line.type == DiffLine.Type.DEL
                        val bg = when {
                            isAdd -> AddBg
                            isDel -> DelBg
                            else -> Color.Transparent
                        }
                        val fg = when {
                            isAdd -> AddFg
                            isDel -> DelFg
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(bg)
                                .padding(vertical = 1.dp)
                        ) {
                            Text(
                                if (isAdd) "+ " else if (isDel) "− " else "  ",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = fg,
                            )
                            Text(
                                line.text.ifEmpty { " " },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = fg,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(stringResource(R.string.diff_removed), fontSize = 10.sp, color = DelFg)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.diff_added), fontSize = 10.sp, color = AddFg)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onApply) { Text(stringResource(R.string.ai_apply_run)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
