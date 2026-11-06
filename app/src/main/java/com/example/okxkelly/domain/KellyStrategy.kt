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

        rsi[periods] = when { avgLoss == 0.0 && avgGain == 0.0 -> 50.0; avgLoss == 0.0 -> 100.0; else -> 100.0 - (100.0 / (1.0 + avgGain / avgLoss)) }

        for (i in (periods + 1) until candles.size) {
            val change = candles[i].close - candles[i - 1].close
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) -change else 0.0

            avgGain = (avgGain * (periods - 1) + gain) / periods
            avgLoss = (avgLoss * (periods - 1) + loss) / periods

            rsi[i] = when { avgLoss == 0.0 && avgGain == 0.0 -> 50.0; avgLoss == 0.0 -> 100.0; else -> 100.0 - (100.0 / (1.0 + avgGain / avgLoss)) }
        }

        return rsi
    }

    fun backtestStrategy(
        candles: List<Candle>,
        tpPercent: Double = 0.02,
        slPercent: Double = 0.01
    ): SimMetrics {
        require(tpPercent.isFinite() && tpPercent in 0.0001..0.5) { "Take profit must be between 0.01% and 50%" }
        require(slPercent.isFinite() && slPercent in 0.0001..0.5) { "Stop loss must be between 0.01% and 50%" }
        val bars = candles.filter { it.confirm }.distinctBy { it.timestamp }.sortedBy { it.timestamp }
        val rsi = calculateRSI(bars, 14)
        val trades = mutableListOf<Trade>()
        var openTrade: Trade? = null
        for (i in 15 until bars.size) {
            val candle = bars[i]
            if (openTrade != null) {
                val highestProfit = (candle.high - openTrade.entryPrice) / openTrade.entryPrice
                val lowestDrop = (candle.low - openTrade.entryPrice) / openTrade.entryPrice
                // Conservative intrabar assumption: if both levels are touched, assume the stop is hit first.
                if (lowestDrop <= -slPercent) {
                    trades.add(openTrade.copy(exitPrice = openTrade.entryPrice * (1.0 - slPercent), profitRatio = -slPercent, won = false, exitTime = candle.timestamp))
                    openTrade = null
                } else if (highestProfit >= tpPercent) {
                    trades.add(openTrade.copy(exitPrice = openTrade.entryPrice * (1.0 + tpPercent), profitRatio = tpPercent, won = true, exitTime = candle.timestamp))
                    openTrade = null
                }
            }
            if (openTrade == null && rsi[i - 1] < 30.0 && rsi[i] >= 30.0) {
                openTrade = Trade(type = "BUY", entryPrice = candle.close, entryTime = candle.timestamp)
            }
        }
        val wins = trades.count { it.won == true }
        val losses = trades.size - wins
        val winRate = if (trades.isNotEmpty()) wins.toDouble() / trades.size else 0.0
        val averageWin = if (wins > 0) trades.filter { it.won == true }.mapNotNull { it.profitRatio }.average() else tpPercent
        val averageLoss = if (losses > 0) trades.filter { it.won == false }.mapNotNull { it.profitRatio }.map { kotlin.math.abs(it) }.average() else slPercent
        val b = if (averageLoss > 0.0) averageWin / averageLoss else 0.0
        val kelly = if (trades.isNotEmpty() && b > 0.0) winRate - ((1.0 - winRate) / b) else 0.0
        return SimMetrics(trades.size, wins, losses, winRate, averageWin, averageLoss, b, kelly.coerceIn(0.0, 1.0))
    }
}
