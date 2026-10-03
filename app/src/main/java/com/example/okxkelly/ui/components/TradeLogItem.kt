package com.example.okxkelly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.okxkelly.data.model.Trade
import com.example.okxkelly.ui.theme.BorderDark
import com.example.okxkelly.ui.theme.LossRed
import com.example.okxkelly.ui.theme.SurfaceVariantDark
import com.example.okxkelly.ui.theme.TextMuted
import com.example.okxkelly.ui.theme.TextPrimary
import com.example.okxkelly.ui.theme.TextSecondary
import com.example.okxkelly.ui.theme.WinGreen
import java.util.Locale

@Composable
fun TradeLogItem(
    trade: Trade,
    modifier: Modifier = Modifier
) {
    val isWon = trade.won == true
    val profitPct = (trade.profitRatio ?: 0.0) * 100

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceVariantDark.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(
                        if (isWon) WinGreen.copy(alpha = 0.15f) else LossRed.copy(alpha = 0.15f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = trade.type,
                    color = if (isWon) WinGreen else LossRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = String.format(Locale.US, "Vào: $%.2f", trade.entryPrice),
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
                trade.exitPrice?.let { exit ->
                    Text(
                        text = String.format(Locale.US, "Ra: $%.2f", exit),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = String.format(Locale.US, "%s%.1f%%", if (profitPct >= 0) "+" else "", profitPct),
                color = if (isWon) WinGreen else LossRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        if (isWon) WinGreen.copy(alpha = 0.2f) else LossRed.copy(alpha = 0.2f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isWon) "W" else "L",
                    color = if (isWon) WinGreen else LossRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
