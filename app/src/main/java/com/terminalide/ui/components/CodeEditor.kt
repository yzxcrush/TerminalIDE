package com.terminalide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalide.core.Language
import com.terminalide.ui.theme.GutterBackground

/**
 * 代码编辑器：等宽字体 + 行号 + 语法高亮 + 括号自动配对 + 回车自动缩进
 * + 底部「代码符号工具条」（手机键盘没有的符号一键输入）。
 */
@Composable
fun CodeEditor(
    code: String,
    language: Language,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = remember { SyntaxHighlighter.defaultPalette }
    val codeStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = palette.plain,
    )

    val visualTransformation = remember(language) {
        VisualTransformation { text ->
            TransformedText(
                SyntaxHighlighter.highlight(text.text, language, palette),
                OffsetMapping.Identity,
            )
        }
    }

    var fieldValue by remember { mutableStateOf(TextFieldValue(code)) }

    // 外部来源（切语言 / 载入文件 / AI 插入）改变整段代码时同步
    LaunchedEffect(code) {
        if (fieldValue.text != code) {
            fieldValue = TextFieldValue(code, TextRange(code.length))
        }
    }

    val lineNumbers = remember(fieldValue.text) {
        val count = fieldValue.text.count { it == '\n' } + 1
        buildString { for (i in 1..count) append(i).append('\n') }
    }

    fun applyText(newText: String, selection: TextRange) {
        fieldValue = TextFieldValue(newText, selection)
        onCodeChange(newText)
    }

    fun insertAtCursor(s: String) {
        val text = fieldValue.text
        val start = fieldValue.selection.min.coerceIn(0, text.length)
        val end = fieldValue.selection.max.coerceIn(0, text.length)
        val newText = text.substring(0, start) + s + text.substring(end)
        applyText(newText, TextRange(start + s.length))
    }

    fun handleChange(new: TextFieldValue) {
        val oldText = fieldValue.text
        val newText = new.text
        val delta = newText.length - oldText.length

        if (delta == 1 && new.selection.collapsed) {
            val pos = new.selection.start
            val ch = if (pos in 1..newText.length) newText[pos - 1] else ' '
            // —— 输入闭合符且下一个字符正是它 → 跳过，避免重复 ——
            if (ch == ')' || ch == ']' || ch == '}') {
                if (pos < newText.length && newText[pos] == ch) {
                    val without = newText.substring(0, pos) + newText.substring(pos + 1)
                    applyText(without, TextRange(pos))
                    return
                }
            }
            // —— 括号自动配对 ——
            val close = when (ch) {
                '(' -> ")"
                '[' -> "]"
                '{' -> "}"
                else -> null
            }
            if (close != null) {
                val paired = newText.substring(0, pos) + close + newText.substring(pos)
                applyText(paired, TextRange(pos))
                return
            }
            // —— 回车自动缩进（Python 冒号后额外缩进 4 空格）——
            if (ch == '\n') {
                val prevLineStart = newText.lastIndexOf('\n', (pos - 2).coerceAtLeast(0))
                    .let { if (it < 0) 0 else it + 1 }
                val currentLine = newText.substring(prevLineStart, pos - 1)
                val indent = currentLine.takeWhile { it == ' ' || it == '\t' }
                val extra = if (language == Language.PYTHON && currentLine.trimEnd().endsWith(":")) "    " else ""
                if (indent.isNotEmpty() || extra.isNotEmpty()) {
                    val ins = indent + extra
                    val withIndent = newText.substring(0, pos) + ins + newText.substring(pos)
                    applyText(withIndent, TextRange(pos + ins.length))
                    return
                }
            }
        }
        fieldValue = new
        onCodeChange(newText)
    }

    Column(modifier) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0B1119))
        ) {
            Row(Modifier.fillMaxSize()) {
                // 行号栏
                Text(
                    text = lineNumbers,
                    style = codeStyle.copy(color = Color(0xFF5A6B7C), fontSize = 12.sp),
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .width(46.dp)
                        .fillMaxHeight()
                        .background(GutterBackground)
                        .padding(horizontal = 8.dp, vertical = 14.dp),
                )
                // 分隔线
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF1E2A38))
                )
                // 编辑区
                BasicTextField(
                    value = fieldValue,
                    onValueChange = ::handleChange,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    textStyle = codeStyle,
                    visualTransformation = visualTransformation,
                    cursorBrush = SolidColor(Color(0xFF64B5F6)),
                    decorationBox = { innerTextField ->
                        Box(Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
                            innerTextField()
                        }
                    },
                )
            }
            // 右下角水印
            Text(
                "@惜白",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 6.dp),
                color = Color.White.copy(alpha = 0.16f),
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
            )
        }
        CodeKeyboardBar(onInsert = ::insertAtCursor)
    }
}

/** 手机上写代码用的符号工具条（可横向滚动）。 */
@Composable
fun CodeKeyboardBar(onInsert: (String) -> Unit, modifier: Modifier = Modifier) {
    val symbols = listOf(
        "(" to "(", ")" to ")", "[" to "[", "]" to "]", "{" to "{", "}" to "}",
        ":" to ":", "\"" to "\"", "'" to "'", "=" to "=", "+" to "+", "-" to "-",
        "*" to "*", "/" to "/", "#" to "#", "," to ",", "." to ".", "_" to "_",
        "<" to "<", ">" to ">", "&" to "&", "|" to "|", "!" to "!", "\\" to "\\",
        "TAB" to "    ", "⏎" to "\n",
    )
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        symbols.forEach { (label, value) ->
            TextButton(
                onClick = { onInsert(value) },
                contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp),
            ) {
                Text(
                    label,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
