package com.example.okxkelly.domain

import com.example.okxkelly.data.model.Candle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KellyStrategyTest {
    private fun candle(index: Int, close: Double, high: Double = close, low: Double = close, confirmed: Boolean = true) =
        Candle(index.toLong() * 60_000, close, high, low, close, 0.0, 0.0, 0.0, confirmed)

    @Test fun rsiIsBoundedAndFlatSeriesDoesNotInventLosses() {
        val flat = (0..20).map { candle(it, 100.0) }
        val rsi = KellyStrategy.calculateRSI(flat)
        assertEquals(50.0, rsi.last(), 0.000001)
        assertTrue(rsi.all { it in 0.0..100.0 })
    }

    @Test fun ignoresUnconfirmedCandlesAndReturnsZeroKellyWithoutClosedTrades() {
        val prices = (0..40).map { candle(it, 100.0 + it, confirmed = true) } + candle(41, 20.0, confirmed = false)
        val metrics = KellyStrategy.backtestStrategy(prices)
        assertEquals(0, metrics.totalTrades)
        assertEquals(0.0, metrics.kellyFraction, 0.0)
    }

    @Test fun stopLossWinsWhenTpAndSlAreBothTouchedInSameBar() {
        val candles = (0..14).map { candle(it, 100.0 - it) }.toMutableList()
        candles += candle(15, 100.0)
        candles += candle(16, 100.0, high = 103.0, low = 95.0)
        val metrics = KellyStrategy.backtestStrategy(candles, tpPercent = 0.02, slPercent = 0.01)
        assertEquals(1, metrics.totalTrades)
        assertEquals(0, metrics.wins)
        assertEquals(1, metrics.losses)
        assertEquals(0.0, metrics.kellyFraction, 0.0)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidRiskParameters() {
        KellyStrategy.backtestStrategy(emptyList(), tpPercent = -0.1, slPercent = 0.01)
    }
}
