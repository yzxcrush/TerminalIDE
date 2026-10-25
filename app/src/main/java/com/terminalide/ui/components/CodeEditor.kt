package com.terminalide.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminalide.core.Language
import com.terminalide.ui.theme.GutterBackground

/**
 * 代码编辑器：等宽字体 + 行号 + 语法高亮（VisualTransformation 着色，光标/选择不受影响）。
 * 外观为圆角卡片，行号栏与代码区之间有细分隔线。
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

    Box(
        modifier
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
            // 编辑区（语法高亮）
            BasicTextField(
                value = code,
                onValueChange = onCodeChange,
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
    }
}
