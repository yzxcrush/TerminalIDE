package com.terminalide.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalide.R
import com.terminalide.core.Language
import com.terminalide.ui.theme.GutterBackground

private val MatchBg = Color(0x40FFD54F)
private val CurrentMatchBg = Color(0x99FFB300)

/**
 * 代码编辑器：等宽字体 + 行号 + 语法高亮 + 括号自动配对 + 回车自动缩进
 * + 撤销/重做 + 查找替换 + 底部「代码符号工具条」。
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

    var fieldValue by remember { mutableStateOf(TextFieldValue(code)) }
    val undoStack = remember { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember { mutableStateListOf<TextFieldValue>() }

    // 查找/替换状态
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var matchIndex by remember { mutableStateOf(0) }

    val matches = remember(query, fieldValue.text) {
        if (query.isEmpty()) return@remember emptyList()
        val text = fieldValue.text
        val out = mutableListOf<IntRange>()
        var i = text.indexOf(query, 0, ignoreCase = true)
        while (i >= 0) {
            out.add(i until i + query.length)
            i = text.indexOf(query, i + 1, ignoreCase = true)
        }
        out
    }

    // 外部来源（切语言 / 载入文件 / AI 插入）改变整段代码时同步
    LaunchedEffect(code) {
        if (fieldValue.text != code) {
            fieldValue = TextFieldValue(code, TextRange(code.length))
        }
    }

    val visualTransformation = remember(language, matches, matchIndex) {
        VisualTransformation { text ->
            val base = SyntaxHighlighter.highlight(text.text, language, palette)
            val annotated = buildAnnotatedString {
                append(base)
                matches.forEachIndexed { idx, r ->
                    val bg = if (idx == matchIndex) CurrentMatchBg else MatchBg
                    addStyle(
                        SpanStyle(background = bg),
                        r.first.coerceIn(0, text.text.length),
                        (r.last + 1).coerceIn(0, text.text.length),
                    )
                }
            }
            TransformedText(annotated, OffsetMapping.Identity)
        }
    }

    val lineNumbers = remember(fieldValue.text) {
        val count = fieldValue.text.count { it == '\n' } + 1
        buildString { for (i in 1..count) append(i).append('\n') }
    }

    fun pushUndo() {
        undoStack.add(fieldValue)
        if (undoStack.size > 200) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun applyText(newText: String, selection: TextRange) {
        fieldValue = TextFieldValue(newText, selection)
        onCodeChange(newText)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val prev = undoStack.removeAt(undoStack.size - 1)
        redoStack.add(fieldValue)
        fieldValue = prev
        onCodeChange(prev.text)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeAt(redoStack.size - 1)
        undoStack.add(fieldValue)
        fieldValue = next
        onCodeChange(next.text)
    }

    fun gotoMatch(idx: Int) {
        if (matches.isEmpty()) return
        val i = ((idx % matches.size) + matches.size) % matches.size
        matchIndex = i
        fieldValue = fieldValue.copy(selection = TextRange(matches[i].first, matches[i].last + 1))
    }

    fun replaceCurrent() {
        if (matches.isEmpty()) return
        val r = matches[matchIndex.coerceIn(0, matches.size - 1)]
        val text = fieldValue.text
        pushUndo()
        val newText = text.substring(0, r.first) + replaceText + text.substring(r.last + 1)
        applyText(newText, TextRange(r.first + replaceText.length))
    }

    fun replaceAll() {
        if (query.isEmpty()) return
        pushUndo()
        val newText = fieldValue.text.replace(query, replaceText, ignoreCase = true)
        applyText(newText, TextRange(0))
    }

    fun insertAtCursor(s: String) {
        val text = fieldValue.text
        val start = fieldValue.selection.min.coerceIn(0, text.length)
        val end = fieldValue.selection.max.coerceIn(0, text.length)
        val newText = text.substring(0, start) + s + text.substring(end)
        pushUndo()
        applyText(newText, TextRange(start + s.length))
    }

    fun handleChange(new: TextFieldValue) {
        val oldText = fieldValue.text
        val newText = new.text
        if (newText == oldText) {
            fieldValue = new
            return
        }
        val delta = newText.length - oldText.length
        val isAutoEdit = delta == 1 && new.selection.collapsed

        // 记录到撤销栈（自动配对/缩进也在内，一次 undo 撤销整次编辑）
        pushUndo()

        if (isAutoEdit) {
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
        // ---- 工具栏：撤销 / 重做 / 查找 ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { undo() },
                enabled = undoStack.isNotEmpty(),
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    Icons.Filled.Undo,
                    contentDescription = stringResource(R.string.editor_undo),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { redo() },
                enabled = redoStack.isNotEmpty(),
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    Icons.Filled.Redo,
                    contentDescription = stringResource(R.string.editor_redo),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            if (matches.isNotEmpty()) {
                Text(
                    "${matchIndex + 1}/${matches.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
                IconButton(onClick = { gotoMatch(matchIndex - 1) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上一个")
                }
                IconButton(onClick = { gotoMatch(matchIndex + 1) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下一个")
                }
            } else if (query.isNotEmpty()) {
                Text(
                    stringResource(R.string.editor_no_match),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                )
            }
            IconButton(
                onClick = {
                    showSearch = !showSearch
                    if (!showSearch) query = ""
                },
                modifier = Modifier.size(34.dp),
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.editor_search),
                    tint = if (showSearch) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showSearch) {
            FindReplaceBar(
                query = query,
                onQueryChange = {
                    query = it
                    matchIndex = 0
                },
                replaceText = replaceText,
                onReplaceTextChange = { replaceText = it },
                onReplace = { replaceCurrent() },
                onReplaceAll = { replaceAll() },
                onClose = {
                    showSearch = false
                    query = ""
                },
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0B1119))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.02f),
                        )
                    ),
                    shape = RoundedCornerShape(16.dp),
                )
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

/** 查找 / 替换栏。 */
@Composable
private fun FindReplaceBar(
    query: String,
    onQueryChange: (String) -> Unit,
    replaceText: String,
    onReplaceTextChange: (String) -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            placeholder = { Text(stringResource(R.string.editor_find_hint), fontSize = 12.sp) },
            textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", modifier = Modifier.size(16.dp))
                }
            },
        )
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = replaceText,
                onValueChange = onReplaceTextChange,
                modifier = Modifier.weight(1f).height(42.dp),
                placeholder = { Text(stringResource(R.string.editor_replace_hint), fontSize = 12.sp) },
                textStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                singleLine = true,
            )
            TextButton(onClick = onReplace, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text(stringResource(R.string.editor_replace), fontSize = 11.sp)
            }
            TextButton(onClick = onReplaceAll, contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text(stringResource(R.string.editor_replace_all), fontSize = 11.sp)
            }
        }
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
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        symbols.forEach { (label, value) ->
            TextButton(
                onClick = { onInsert(value) },
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 0.dp),
                modifier = Modifier.height(40.dp),
            ) {
                Text(
                    label,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
