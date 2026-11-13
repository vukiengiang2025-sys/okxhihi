package com.example.okxkelly.domain

import com.example.okxkelly.data.model.Candle
import com.example.okxkelly.data.model.SimMetrics
import com.example.okxkelly.data.model.Trade
import kotlin.math.abs

/** A backtest snapshot keeps open positions separate from closed-trade statistics. */
data class BacktestResult(
    val metrics: SimMetrics,
    val closedTrades: List<Trade>,
    val openTrade: Trade?
)

object KellyStrategy {
    const val RSI_PERIODS = 14
    const val OVERSOLD_LEVEL = 30.0
    const val MIN_ENTRY_INDEX = RSI_PERIODS + 2

    /** Sorts bars and only collapses same-timestamp duplicates when their OHLC data agrees. */
    fun normalizeCandles(candles: List<Candle>): List<Candle> {
        val byTimestamp = sortedMapOf<Long, Candle>()
        for (candle in candles) {
            val previous = byTimestamp[candle.timestamp]
            if (previous == null) {
                byTimestamp[candle.timestamp] = candle
            } else {
                val ohlcMatches = previous.open == candle.open && previous.high == candle.high &&
                    previous.low == candle.low && previous.close == candle.close
                require(ohlcMatches) { "Conflicting OHLC data for candle timestamp ${candle.timestamp}" }
                if (candle.confirm) byTimestamp[candle.timestamp] = candle
            }
        }
        return byTimestamp.values.toList()
    }

    fun hasCandleGap(previousTimestamp: Long, candles: List<Candle>, expectedIntervalMillis: Long): Boolean {
        require(previousTimestamp > 0L) { "Previous candle timestamp must be positive" }
        require(expectedIntervalMillis > 0L) { "Expected interval must be positive" }
        val timestamps = candles.map { it.timestamp }.distinct().sorted()
        if (timestamps.isEmpty()) return false
        if (timestamps.first() - previousTimestamp > expectedIntervalMillis) return true
        return timestamps.zipWithNext().any { (previous, current) -> current - previous > expectedIntervalMillis }
    }

    fun hasInternalCandleGap(candles: List<Candle>, expectedIntervalMillis: Long): Boolean {
        require(expectedIntervalMillis > 0L) { "Expected interval must be positive" }
        val timestamps = candles.map { it.timestamp }.distinct().sorted()
        return timestamps.zipWithNext().any { (previous, current) -> current - previous > expectedIntervalMillis }
    }

    /** Values before the first complete Wilder RSI window are NaN, never a false oversold reading. */
    fun calculateRSI(candles: List<Candle>, periods: Int = RSI_PERIODS): DoubleArray {
        require(periods > 0) { "RSI periods must be positive" }
        val rsi = DoubleArray(candles.size) { Double.NaN }
        if (candles.size < periods + 1) return rsi

        var gains = 0.0
        var losses = 0.0
        for (i in 1..periods) {
            val change = candles[i].close - candles[i - 1].close
            if (change > 0.0) gains += change else losses -= change
        }
        var avgGain = gains / periods
        var avgLoss = losses / periods
        rsi[periods] = rsiValue(avgGain, avgLoss)

        for (i in (periods + 1) until candles.size) {
            val change = candles[i].close - candles[i - 1].close
            val gain = if (change > 0.0) change else 0.0
            val loss = if (change < 0.0) -change else 0.0
            avgGain = (avgGain * (periods - 1) + gain) / periods
            avgLoss = (avgLoss * (periods - 1) + loss) / periods
            rsi[i] = rsiValue(avgGain, avgLoss)
        }
        return rsi
    }

    private fun rsiValue(avgGain: Double, avgLoss: Double): Double = when {
        avgLoss == 0.0 && avgGain == 0.0 -> 50.0
        avgLoss == 0.0 -> 100.0
        else -> 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
    }

    /** currentIndex is the bar whose close has just formed the RSI crossover. */
    fun crossedAboveOversold(rsi: DoubleArray, currentIndex: Int): Boolean =
        currentIndex > 0 && currentIndex < rsi.size &&
            rsi[currentIndex - 1].isFinite() && rsi[currentIndex].isFinite() &&
            rsi[currentIndex - 1] < OVERSOLD_LEVEL && rsi[currentIndex] >= OVERSOLD_LEVEL

    /** Returns the realized TP/SL ratio. If both levels are touched, assumes SL first. */
    fun exitRatioOnCandle(entryPrice: Double, candle: Candle, tpPercent: Double, slPercent: Double): Double? {
        require(entryPrice.isFinite() && entryPrice > 0.0) { "Entry price must be finite and positive" }
        require(tpPercent.isFinite() && tpPercent > 0.0) { "Take profit must be finite and positive" }
        require(slPercent.isFinite() && slPercent > 0.0) { "Stop loss must be finite and positive" }
        val highestProfit = (candle.high - entryPrice) / entryPrice
        val lowestDrop = (candle.low - entryPrice) / entryPrice
        return when {
            lowestDrop <= -slPercent -> -slPercent
            highestProfit >= tpPercent -> tpPercent
            else -> null
        }
    }

    fun backtestStrategy(
        candles: List<Candle>,
        tpPercent: Double = 0.02,
        slPercent: Double = 0.01,
        expectedIntervalMillis: Long? = null
    ): SimMetrics = backtestDetailed(candles, tpPercent, slPercent, expectedIntervalMillis).metrics

    /**
     * Signals are known only after bar close and enter at the next bar's open.
     * TP/SL are then evaluated against that entry bar's range. An exit prevents
     * a same-bar re-entry, matching the forward simulator's execution convention.
     */
    fun backtestDetailed(
        candles: List<Candle>,
        tpPercent: Double = 0.02,
        slPercent: Double = 0.01,
        expectedIntervalMillis: Long? = null
    ): BacktestResult {
        require(tpPercent.isFinite() && tpPercent in 0.0001..0.5) { "Take profit must be between 0.01% and 50%" }
        require(slPercent.isFinite() && slPercent in 0.0001..0.5) { "Stop loss must be between 0.01% and 50%" }
        expectedIntervalMillis?.let { require(it > 0L) { "Expected interval must be positive" } }

        val bars = normalizeCandles(candles.filter { it.confirm })
        require(bars.all { it.timestamp > 0L }) { "Candle timestamps must be positive" }
        if (expectedIntervalMillis != null && hasInternalCandleGap(bars, expectedIntervalMillis)) {
            error("Historical candles contain a gap; backtest stopped to avoid misleading results")
        }
        val rsi = calculateRSI(bars, RSI_PERIODS)
        val closedTrades = mutableListOf<Trade>()
        var openTrade: Trade? = null

        for (index in MIN_ENTRY_INDEX until bars.size) {
            val candle = bars[index]
            if (openTrade == null && crossedAboveOversold(rsi, index - 1)) {
                openTrade = Trade(type = "BUY", entryPrice = candle.open, entryTime = candle.timestamp)
            }

            val activeTrade = openTrade
            if (activeTrade != null) {
                val realizedRatio = exitRatioOnCandle(activeTrade.entryPrice, candle, tpPercent, slPercent)
                if (realizedRatio != null) {
                    val won = realizedRatio > 0.0
                    closedTrades += activeTrade.copy(
                        exitPrice = activeTrade.entryPrice * (1.0 + realizedRatio),
                        profitRatio = realizedRatio,
                        won = won,
                        exitTime = candle.timestamp
                    )
                    openTrade = null
                }
            }

            // The current bar's close can only trigger an entry on the next bar.
        }

        val wins = closedTrades.count { it.won == true }
        val losses = closedTrades.size - wins
        val winRate = if (closedTrades.isNotEmpty()) wins.toDouble() / closedTrades.size else 0.0
        val averageWin = if (wins > 0) closedTrades.filter { it.won == true }.mapNotNull { it.profitRatio }.average() else tpPercent
        val averageLoss = if (losses > 0) closedTrades.filter { it.won == false }.mapNotNull { it.profitRatio }.map(::abs).average() else slPercent
        val b = if (averageLoss > 0.0) averageWin / averageLoss else 0.0
        val rawKelly = if (closedTrades.isNotEmpty() && b > 0.0) winRate - ((1.0 - winRate) / b) else 0.0
        val metrics = SimMetrics(
            totalTrades = closedTrades.size,
            wins = wins,
            losses = losses,
            winRate = winRate,
            averageWinRatio = averageWin,
            averageLossRatio = averageLoss,
            b = b,
            kellyFraction = rawKelly.coerceIn(0.0, 1.0)
        )
        return BacktestResult(metrics, closedTrades, openTrade)
    }
}
