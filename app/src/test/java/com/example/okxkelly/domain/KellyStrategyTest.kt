package com.example.okxkelly.domain

import com.example.okxkelly.data.model.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KellyStrategyTest {
    private fun candle(
        index: Int,
        close: Double,
        open: Double = close,
        high: Double = maxOf(open, close),
        low: Double = minOf(open, close),
        confirmed: Boolean = true
    ) = Candle(index.toLong() * 60_000, open, high, low, close, 0.0, 0.0, 0.0, confirmed)

    @Test fun rsiWarmupIsInvalidAndFlatSeriesIsNeutral() {
        val rsi = KellyStrategy.calculateRSI((0..20).map { candle(it, 100.0) })
        assertTrue(rsi.take(KellyStrategy.RSI_PERIODS).all { it.isNaN() })
        assertEquals(50.0, rsi[KellyStrategy.RSI_PERIODS], 0.000001)
        assertTrue(rsi.drop(KellyStrategy.RSI_PERIODS).all { it in 0.0..100.0 })
    }

    @Test fun rsiReturnsExpectedExtremesForOneWayPriceSeries() {
        val rising = KellyStrategy.calculateRSI((0..20).map { candle(it, 100.0 + it) })
        val falling = KellyStrategy.calculateRSI((0..20).map { candle(it, 100.0 - it) })
        assertEquals(100.0, rising.last(), 0.000001)
        assertEquals(0.0, falling.last(), 0.000001)
        assertFalse(KellyStrategy.crossedAboveOversold(rising, KellyStrategy.RSI_PERIODS))
    }

    @Test fun ignoresUnconfirmedCandlesAndReturnsZeroKellyWithoutClosedTrades() {
        val prices = (0..40).map { candle(it, 100.0 + it) } + candle(41, 20.0, confirmed = false)
        val metrics = KellyStrategy.backtestStrategy(prices)
        assertEquals(0, metrics.totalTrades)
        assertEquals(0.0, metrics.kellyFraction, 0.0)
    }

    @Test fun signalAtCloseEntersAtNextBarOpenNotSignalBarClose() {
        val candles = (0..14).map { candle(it, 100.0 - it) }.toMutableList()
        candles += candle(15, close = 100.0)
        candles += candle(16, close = 101.0, open = 98.5, high = 101.0, low = 98.0)
        candles += candle(17, close = 101.0)

        val result = KellyStrategy.backtestDetailed(candles, tpPercent = 0.05, slPercent = 0.05)
        assertEquals(98.5, result.openTrade?.entryPrice ?: Double.NaN, 0.0)
        assertEquals(16L * 60_000, result.openTrade?.entryTime ?: -1L)
        assertEquals(0, result.metrics.totalTrades)
    }

    @Test fun entryBarChecksStopsAndBothLevelsUseConservativeStopFirst() {
        val candles = (0..14).map { candle(it, 100.0 - it) }.toMutableList()
        candles += candle(15, 100.0)
        candles += candle(16, close = 100.0, open = 99.0, high = 103.0, low = 95.0)

        val result = KellyStrategy.backtestDetailed(candles, tpPercent = 0.02, slPercent = 0.01)
        assertEquals(1, result.metrics.totalTrades)
        assertEquals(0, result.metrics.wins)
        assertEquals(1, result.metrics.losses)
        assertEquals(99.0, result.closedTrades.single().entryPrice, 0.0)
        assertEquals(98.01, result.closedTrades.single().exitPrice!!, 0.000001)
        assertNull(result.openTrade)
    }

    @Test fun normalizesMatchingDuplicatesButRejectsConflictingOhlc() {
        val first = candle(1, close = 100.0)
        assertEquals(1, KellyStrategy.normalizeCandles(listOf(first, first)).size)
        try {
            KellyStrategy.normalizeCandles(listOf(first, candle(1, close = 101.0)))
            throw AssertionError("Expected conflicting timestamp to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("Conflicting OHLC"))
        }
    }

    @Test fun gapInHistoricalBarsRejectsBacktestWhenIntervalIsKnown() {
        val candles = (0..20).filter { it != 8 }.map { candle(it, 100.0 + it) }
        try {
            KellyStrategy.backtestDetailed(candles, expectedIntervalMillis = 60_000L)
            throw AssertionError("Expected backtest to reject missing bars")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("gap"))
        }
    }

    @Test fun candleGapDetectionAllowsContiguousDataAndFlagsMissingBars() {
        val bars = listOf(candle(2, 100.0), candle(3, 101.0))
        assertFalse(KellyStrategy.hasCandleGap(60_000L, bars, 60_000L))
        assertTrue(KellyStrategy.hasCandleGap(60_000L, listOf(candle(3, 101.0), candle(5, 102.0)), 60_000L))
    }

    @Test fun exitPrioritizesStopWhenBothLevelsAreTouched() {
        val bar = candle(1, close = 100.0, open = 100.0, high = 103.0, low = 98.0)
        assertEquals(-0.01, KellyStrategy.exitRatioOnCandle(100.0, bar, 0.02, 0.01)!!, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveRsiPeriod() {
        KellyStrategy.calculateRSI(emptyList(), periods = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidRiskParameters() {
        KellyStrategy.backtestStrategy(emptyList(), tpPercent = -0.1, slPercent = 0.01)
    }
}
