package com.terminalide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.rememberTextFieldScrollerPosition
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalide.core.Language

/**
 * 代码编辑器：等宽字体 + 行号 + 语法高亮（通过 VisualTransformation 着色，光标/选择不受影响）。
 *
 * @param code        当前源码
 * @param language    当前语言（决定高亮规则）
 * @param onCodeChange 内容变更回调
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

    val lineNumbers = remember(code) {
        val count = code.count { it == '\n' } + 1
        buildString { for (i in 1..count) append(i).append('\n') }
    }

    val scroller = rememberTextFieldScrollerPosition()

    Row(modifier) {
        // 行号栏：与编辑区共享滚动偏移，负向位移实现同步
        Box(
            modifier = Modifier
                .width(52.dp)
                .fillMaxHeight()
                .clipToBounds()
                .background(Color(0xFF10161E)),
        ) {
            Text(
                text = lineNumbers,
                style = codeStyle.copy(color = Color(0xFF6E7A87)),
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
                    .offset { IntOffset(0, -scroller.value.y.toInt()) }
                    .padding(top = 12.dp),
            )
        }

        // 编辑区
        BasicTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            textStyle = codeStyle,
            visualTransformation = visualTransformation,
            scrollState = scroller,
            cursorBrush = SolidColor(Color(0xFF64B5F6)),
            decorationBox = { innerTextField ->
                Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                    innerTextField()
                }
            },
        )
    }
}
