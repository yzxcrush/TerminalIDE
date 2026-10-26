package com.terminalide.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** 深色玻璃底色。 */
val GlassBase = Color(0xFF101827)

/**
 * 「液态玻璃」表面：半透明渐变底 + 顶部高光描边 + 大圆角。
 * Compose 无真·背景模糊，这里用「半透明 + 高光边 + 圆角」近似 iOS 液态玻璃观感。
 */
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(20.dp),
    base: Color = GlassBase,
    alpha: Float = 0.72f,
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(
                base.copy(alpha = alpha),
                base.copy(alpha = (alpha * 0.78f).coerceAtLeast(0f)),
            )
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.18f),
                Color.White.copy(alpha = 0.04f),
            )
        ),
        shape = shape,
    )

/** 玻璃高光按钮底（更亮的半透明白）。 */
fun Modifier.glassHighlight(shape: Shape = RoundedCornerShape(50)): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.05f),
            )
        )
    )
    .border(1.dp, Color.White.copy(alpha = 0.12f), shape)

/** 应用整体背景：深色竖向渐变（让玻璃面板有「透」的观感）。 */
val AppBackgroundBrush = Brush.verticalGradient(
    listOf(
        Color(0xFF0A1018),
        Color(0xFF0E1622),
        Color(0xFF131C2B),
    )
)
