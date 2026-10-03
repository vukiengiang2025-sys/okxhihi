package com.example.okxkelly.domain

import com.example.okxkelly.data.model.Candle
import com.example.okxkelly.data.model.SimMetrics
import com.example.okxkelly.data.model.Trade
import kotlin.math.abs
import kotlin.math.max

object KellyStrategy {

    fun calculateRSI(candles: List<Candle>, periods: Int = 14): DoubleArray {
        val rsi = DoubleArray(candles.size) { 0.0 }
        if (candles.size < periods + 1) return rsi

        var gains = 0.0
        var losses = 0.0

        for (i in 1..periods) {
            val change = candles[i].close - candles[i - 1].close
            if (change > 0) gains += change
            else losses -= change
        }

        var avgGain = gains / periods
        var avgLoss = losses / periods

        rsi[periods] = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))

        for (i in (periods + 1) until candles.size) {
            val change = candles[i].close - candles[i - 1].close
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) -change else 0.0

            avgGain = (avgGain * (periods - 1) + gain) / periods
            avgLoss = (avgLoss * (periods - 1) + loss) / periods

            rsi[i] = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
        }

        return rsi
    }

    fun backtestStrategy(
        candles: List<Candle>,
        tpPercent: Double = 0.02,
        slPercent: Double = 0.01
    ): SimMetrics {
        val rsi = calculateRSI(candles, 14)
        val trades = mutableListOf<Trade>()
        var openTrade: Trade? = null

        for (i in 14 until candles.size) {
            val candle = candles[i]

            // Check if open trade needs to be closed
            if (openTrade != null) {
                val highestProfit = (candle.high - openTrade.entryPrice) / openTrade.entryPrice
                val lowestDrop = (candle.low - openTrade.entryPrice) / openTrade.entryPrice

                if (lowestDrop <= -slPercent) {
                    val completed = openTrade.copy(
                        exitPrice = openTrade.entryPrice * (1.0 - slPercent),
                        profitRatio = -slPercent,
                        won = false,
                        exitTime = candle.timestamp
                    )
                    trades.add(completed)
                    openTrade = null
                } else if (highestProfit >= tpPercent) {
                    val completed = openTrade.copy(
                        exitPrice = openTrade.entryPrice * (1.0 + tpPercent),
                        profitRatio = tpPercent,
                        won = true,
                        exitTime = candle.timestamp
                    )
                    trades.add(completed)
                    openTrade = null
                }
            }

            // Check entry logic if no open trade: RSI < 30 cross up to >= 30
            if (openTrade == null && rsi[i - 1] != 0.0 && rsi[i - 1] < 30.0 && rsi[i] >= 30.0) {
                openTrade = Trade(
                    type = "BUY",
                    entryPrice = candle.close,
                    entryTime = candle.timestamp
                )
            }
        }

        val completedTrades = trades
        val wins = completedTrades.count { it.won == true }
        val losses = completedTrades.size - wins
        val winRate = if (completedTrades.isNotEmpty()) wins.toDouble() / completedTrades.size else 0.0

        val totalWinRatio = completedTrades.filter { it.won == true }.sumOf { it.profitRatio ?: 0.0 }
        val averageWinRatio = if (wins > 0) totalWinRatio / wins else tpPercent

        val totalLossRatio = completedTrades.filter { it.won != true }.sumOf { abs(it.profitRatio ?: 0.0) }
        val averageLossRatio = if (losses > 0) totalLossRatio / losses else slPercent

        val b = if (averageLossRatio > 0.0) averageWinRatio / averageLossRatio else 1.0

        var kellyFraction = 0.0
        if (completedTrades.isNotEmpty() && b > 0.0) {
            kellyFraction = winRate - ((1.0 - winRate) / b)
        }

        return SimMetrics(
            totalTrades = completedTrades.size,
            wins = wins,
            losses = losses,
            winRate = winRate,
            averageWinRatio = averageWinRatio,
            averageLossRatio = averageLossRatio,
            b = b,
            kellyFraction = max(0.0, kellyFraction)
        )
    }
}
