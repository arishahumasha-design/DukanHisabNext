package com.example.dukanhisabnext.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import com.example.dukanhisabnext.ui.theme.AppTheme

/** A thin dashed line in the border colour (the "paper" look of the receipt). */
@Composable
fun DashedDivider(modifier: Modifier = Modifier) {
    val color = AppTheme.colors.border
    Canvas(modifier = modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
        )
    }
}
