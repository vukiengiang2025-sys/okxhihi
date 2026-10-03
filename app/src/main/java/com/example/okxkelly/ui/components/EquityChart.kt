package com.example.okxkelly.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.okxkelly.data.model.CapitalPoint
import com.example.okxkelly.ui.theme.BorderDark
import com.example.okxkelly.ui.theme.LimeAccent
import com.example.okxkelly.ui.theme.SurfaceDark
import com.example.okxkelly.ui.theme.TextMuted
import com.example.okxkelly.ui.theme.TextPrimary
import com.example.okxkelly.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun EquityChart(
    history: List<CapitalPoint>,
    currentCapital: Double,
    startCapital: Double,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(12.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CURRENT SIMULATED EQUITY (A)",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = String.format(Locale.US, "$%.2f", currentCapital),
                        color = TextPrimary,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                val pnl = currentCapital - startCapital
                val pnlPercent = if (startCapital > 0) (pnl / startCapital) * 100 else 0.0
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "PnL",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "%s$%.2f (%.1f%%)", if (pnl >= 0) "+" else "", pnl, pnlPercent),
                        color = if (pnl >= 0) Color(0xFF34D399) else Color(0xFFF87171),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (history.size < 2) {
                    Text(
                        text = "WAITING FOR SIMULATION DATA...",
                        color = TextMuted,
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Draw 4 dashed horizontal grid lines
                    val gridSteps = 4
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    for (i in 0..gridSteps) {
                        val y = h * (i.toFloat() / gridSteps)
                        drawLine(
                            color = Color(0x1AFFFFFF),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f,
                            pathEffect = dashEffect
                        )
                    }

                    if (history.size < 2) return@Canvas

                    val values = history.map { it.capital }
                    var minVal = values.minOrNull() ?: startCapital
                    var maxVal = values.maxOrNull() ?: startCapital
                    if (minVal == maxVal) {
                        minVal -= 10.0
                        maxVal += 10.0
                    }
                    val range = maxVal - minVal

                    val points = history.mapIndexed { idx, pt ->
                        val x = (idx.toFloat() / (history.size - 1)) * w
                        val y = h - ((pt.capital - minVal) / range).toFloat() * (h * 0.85f) - (h * 0.08f)
                        Offset(x, y)
                    }

                    // Build path
                    val linePath = Path()
                    val fillPath = Path()

                    points.forEachIndexed { i, pt ->
                        if (i == 0) {
                            linePath.moveTo(pt.x, pt.y)
                            fillPath.moveTo(pt.x, h)
                            fillPath.lineTo(pt.x, pt.y)
                        } else {
                            linePath.lineTo(pt.x, pt.y)
                            fillPath.lineTo(pt.x, pt.y)
                        }
                    }

                    fillPath.lineTo(points.last().x, h)
                    fillPath.close()

                    // Draw Gradient Fill
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                LimeAccent.copy(alpha = 0.25f),
                                LimeAccent.copy(alpha = 0.0f)
                            ),
                            startY = 0f,
                            endY = h
                        )
                    )

                    // Draw Stroke Line
                    drawPath(
                        path = linePath,
                        color = LimeAccent,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Draw End Dot
                    val lastPt = points.last()
                    drawCircle(
                        color = LimeAccent.copy(alpha = 0.35f),
                        radius = 8.dp.toPx(),
                        center = lastPt
                    )
                    drawCircle(
                        color = LimeAccent,
                        radius = 4.dp.toPx(),
                        center = lastPt
                    )
                }
            }
        }
    }
}
