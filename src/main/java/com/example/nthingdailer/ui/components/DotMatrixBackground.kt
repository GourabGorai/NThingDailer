package com.example.nthingdailer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.nthingdailer.ui.theme.NothingBlack

@Composable
fun DotMatrixBackground(
    modifier: Modifier = Modifier,
    spacing: Dp = 14.dp,
    dotRadius: Dp = 1.dp,
    dotColor: Color = Color.White.copy(alpha = 0.08f),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NothingBlack)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val spacingPx = spacing.toPx()
            val radiusPx = dotRadius.toPx()
            val cols = (size.width / spacingPx).toInt() + 1
            val rows = (size.height / spacingPx).toInt() + 1

            for (i in 0..cols) {
                for (j in 0..rows) {
                    drawCircle(
                        color = dotColor,
                        radius = radiusPx,
                        center = Offset(i * spacingPx, j * spacingPx)
                    )
                }
            }
        }
        content()
    }
}
