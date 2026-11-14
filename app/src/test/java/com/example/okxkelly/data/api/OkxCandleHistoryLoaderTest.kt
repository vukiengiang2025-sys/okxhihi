package com.example.okxkelly.data.api

import com.example.okxkelly.data.model.OkxCandleResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OkxCandleHistoryLoaderTest {
    private fun row(timestamp: Long, confirmed: Boolean = true) = listOf(
        timestamp.toString(), "100", "101", "99", "100", "1", "1", "100", if (confirmed) "1" else "0"
    )

    @Test
    fun requestsOlderPagesUntilTargetAndUsesOldestTimestampAsCursor() = runBlocking {
        val baseTimestamp = 1_000_000L
        val firstPage = (299 downTo 0).map { row(baseTimestamp + it * 60_000L) }
        val olderPage = listOf(row(baseTimestamp - 60_000L))
        val calls = mutableListOf<Pair<Int, String?>>()
        val pauses = mutableListOf<Long>()

        val result = loadOkxCandleHistory(
            targetCount = 301,
            pause = { pauses += it },
            fetchPage = { limit, after ->
                calls += limit to after
                if (after == null) OkxCandleResponse("0", data = firstPage)
                else OkxCandleResponse("0", data = olderPage)
            }
        )

        assertEquals(301, result.candles.size)
        assertEquals(2, result.pagesFetched)
        assertEquals(300, calls[0].first)
        assertEquals(1, calls[1].first)
        assertEquals(listOf(150L), pauses)
        assertEquals(baseTimestamp.toString(), calls[1].second)
        assertEquals(OkxHistoryStopReason.TARGET_REACHED, result.stopReason)
        assertFalse(result.historyExhausted)
    }

    @Test
    fun continuesAfterShortPageUntilAnEmptyPageConfirmsHistoryEnd() = runBlocking {
        var calls = 0
        val result = loadOkxCandleHistory(
            targetCount = 1_000,
            pause = { _ -> },
            fetchPage = { _, _ ->
                calls++
                if (calls == 1) OkxCandleResponse("0", data = listOf(row(120_000), row(60_000)))
                else OkxCandleResponse("0", data = emptyList())
            }
        )

        assertEquals(2, calls)
        assertEquals(2, result.candles.size)
        assertEquals(2, result.pagesFetched)
        assertEquals(OkxHistoryStopReason.NO_MORE_DATA, result.stopReason)
        assertTrue(result.historyExhausted)
    }

    @Test
    fun continuesFromShortPageWhenOlderRowsStillExist() = runBlocking {
        val baseTimestamp = 10_000_000L
        val calls = mutableListOf<Pair<Int, String?>>()
        val result = loadOkxCandleHistory(
            targetCount = 51,
            pause = { _ -> },
            fetchPage = { limit, after ->
                calls += limit to after
                if (after == null) OkxCandleResponse("0", data = listOf(row(baseTimestamp)))
                else OkxCandleResponse("0", data = (1..50).map { row(baseTimestamp - it * 60_000L) })
            }
        )

        assertEquals(51, result.candles.size)
        assertEquals(2, result.pagesFetched)
        assertEquals(51, calls[0].first)
        assertEquals(50, calls[1].first)
        assertEquals(baseTimestamp.toString(), calls[1].second)
        assertEquals(OkxHistoryStopReason.TARGET_REACHED, result.stopReason)
    }

    @Test
    fun countsOnlyConfirmedCandlesTowardRequestedSample() = runBlocking {
        val firstPage = (51 downTo 2).map { row(it * 60_000L, confirmed = it != 2) }
        var calls = 0
        val result = loadOkxCandleHistory(
            targetCount = 50,
            pause = { _ -> },
            fetchPage = { _, _ ->
                calls++
                if (calls == 1) OkxCandleResponse("0", data = firstPage)
                else OkxCandleResponse("0", data = listOf(row(60_000L)))
            }
        )

        assertEquals(2, calls)
        assertEquals(50, result.candles.size)
        assertTrue(result.candles.all { it.confirm })
        assertEquals(OkxHistoryStopReason.TARGET_REACHED, result.stopReason)
    }

    @Test
    fun retriesOkxRateLimitAndThenContinues() = runBlocking {
        var attempts = 0
        val waits = mutableListOf<Long>()
        val result = loadOkxCandleHistory(
            targetCount = 50,
            pause = { waits += it },
            fetchPage = { _, _ ->
                attempts++
                if (attempts == 1) OkxCandleResponse("50011", msg = "Rate limit reached")
                else OkxCandleResponse("0", data = (50 downTo 1).map { row(it * 60_000L) })
            }
        )

        assertEquals(2, attempts)
        assertEquals(listOf(500L), waits)
        assertEquals(50, result.candles.size)
    }

    @Test
    fun stopsSafelyWhenCursorDoesNotMove() = runBlocking {
        val timestamp = 10_000_000L
        val result = loadOkxCandleHistory(
            targetCount = 51,
            pause = { _ -> },
            fetchPage = { _, _ -> OkxCandleResponse("0", data = listOf(row(timestamp))) }
        )

        assertEquals(2, result.pagesFetched)
        assertEquals(1, result.candles.size)
        assertEquals(OkxHistoryStopReason.CURSOR_STALLED, result.stopReason)
        assertFalse(result.historyExhausted)
    }

    @Test
    fun distinguishesThePageGuardFromExhaustedHistory() = runBlocking {
        var call = 0
        val result = loadOkxCandleHistory(
            targetCount = MAX_BACKTEST_CANDLES,
            pause = { _ -> },
            fetchPage = { _, _ ->
                call++
                OkxCandleResponse("0", data = listOf(row(10_000_000L - (call - 1) * 60_000L)))
            }
        )

        assertEquals(40, result.pagesFetched)
        assertEquals(40, result.candles.size)
        assertEquals(OkxHistoryStopReason.PAGE_LIMIT_REACHED, result.stopReason)
        assertFalse(result.historyExhausted)
    }
}
