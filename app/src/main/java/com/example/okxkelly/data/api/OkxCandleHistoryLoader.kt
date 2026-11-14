package com.example.okxkelly.data.api

import com.example.okxkelly.data.model.Candle
import com.example.okxkelly.data.model.OkxCandleResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException

const val OKX_CANDLE_PAGE_SIZE = 300
const val MAX_BACKTEST_CANDLES = 10_000
private const val MAX_HISTORY_PAGES = 40
private const val INTER_PAGE_DELAY_MS = 150L
private const val MAX_RETRIES = 2

enum class OkxHistoryStopReason {
    TARGET_REACHED,
    NO_MORE_DATA,
    CURSOR_STALLED,
    PAGE_LIMIT_REACHED
}

data class OkxCandleHistoryResult(
    val candles: List<Candle>,
    val requestedCount: Int,
    val pagesFetched: Int,
    val stopReason: OkxHistoryStopReason
) {
    val historyExhausted: Boolean get() = stopReason == OkxHistoryStopReason.NO_MORE_DATA
}

/** Loads confirmed history backwards with the public OKX history-candles cursor. */
suspend fun loadOkxCandleHistory(
    targetCount: Int,
    fetchPage: suspend (limit: Int, after: String?) -> OkxCandleResponse,
    onProgress: suspend (loaded: Int, target: Int, pagesFetched: Int) -> Unit = { _, _, _ -> },
    pause: suspend (millis: Long) -> Unit = { delay(it) }
): OkxCandleHistoryResult {
    require(targetCount in 50..MAX_BACKTEST_CANDLES) { "History count must be between 50 and $MAX_BACKTEST_CANDLES" }

    val candlesByTimestamp = sortedMapOf<Long, Candle>()
    var after: String? = null
    var previousCursor: Long? = null
    var pagesFetched = 0
    var stopReason: OkxHistoryStopReason? = null

    while (candlesByTimestamp.values.count { it.confirm } < targetCount && pagesFetched < MAX_HISTORY_PAGES) {
        if (pagesFetched > 0) pause(INTER_PAGE_DELAY_MS)
        val loadedBeforePage = candlesByTimestamp.values.count { it.confirm }
        val limit = minOf(OKX_CANDLE_PAGE_SIZE, targetCount - loadedBeforePage)
        val response = fetchPageWithRetry(limit, after, fetchPage, pause)
        if (response.code != "0") {
            throw IllegalStateException(response.msg?.takeIf { it.isNotBlank() } ?: "OKX API error (${response.code})")
        }
        pagesFetched++
        if (response.data.isEmpty()) {
            stopReason = OkxHistoryStopReason.NO_MORE_DATA
            break
        }

        val pageTimestamps = response.data.mapNotNull { row ->
            row.firstOrNull()?.toLongOrNull()?.takeIf { it > 0L }
        }
        val oldestTimestamp = pageTimestamps.minOrNull()
            ?: throw IllegalStateException("OKX returned candle rows without valid timestamps")

        response.data.mapNotNull(::parseOkxCandle).forEach { candle ->
            val existing = candlesByTimestamp[candle.timestamp]
            if (existing == null) {
                candlesByTimestamp[candle.timestamp] = candle
            } else {
                val sameOhlc = existing.open == candle.open && existing.high == candle.high &&
                    existing.low == candle.low && existing.close == candle.close
                require(sameOhlc) { "Conflicting OHLC data for candle timestamp ${candle.timestamp}" }
                if (candle.confirm) candlesByTimestamp[candle.timestamp] = candle
            }
        }
        val loadedAfterPage = candlesByTimestamp.values.count { it.confirm }
        onProgress(loadedAfterPage.coerceAtMost(targetCount), targetCount, pagesFetched)

        if (loadedAfterPage >= targetCount) {
            stopReason = OkxHistoryStopReason.TARGET_REACHED
            break
        }
        if (previousCursor != null && oldestTimestamp >= previousCursor) {
            stopReason = OkxHistoryStopReason.CURSOR_STALLED
            break
        }

        previousCursor = oldestTimestamp
        after = oldestTimestamp.toString()
    }

    val confirmedCandles = candlesByTimestamp.values.filter { it.confirm }
    val finalStopReason = stopReason ?: when {
        confirmedCandles.size >= targetCount -> OkxHistoryStopReason.TARGET_REACHED
        pagesFetched >= MAX_HISTORY_PAGES -> OkxHistoryStopReason.PAGE_LIMIT_REACHED
        else -> OkxHistoryStopReason.NO_MORE_DATA
    }
    return OkxCandleHistoryResult(
        candles = confirmedCandles.takeLast(targetCount),
        requestedCount = targetCount,
        pagesFetched = pagesFetched,
        stopReason = finalStopReason
    )
}

private suspend fun fetchPageWithRetry(
    limit: Int,
    after: String?,
    fetchPage: suspend (Int, String?) -> OkxCandleResponse,
    pause: suspend (Long) -> Unit
): OkxCandleResponse {
    var lastError: Exception? = null
    for (attempt in 0..MAX_RETRIES) {
        val response = try {
            fetchPage(limit, after)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (networkError: IOException) {
            lastError = networkError
            if (attempt == MAX_RETRIES) throw networkError
            pause(500L * (attempt + 1))
            continue
        } catch (httpError: HttpException) {
            if (httpError.code() !in 500..599 && httpError.code() != 429) throw httpError
            lastError = httpError
            if (attempt == MAX_RETRIES) throw httpError
            pause(500L * (attempt + 1))
            continue
        }

        if (response.code == "0") return response
        val apiError = IllegalStateException(response.msg?.takeIf { it.isNotBlank() } ?: "OKX API error (${response.code})")
        if (response.code != "50011" || attempt == MAX_RETRIES) throw apiError
        lastError = apiError
        pause(500L * (attempt + 1))
    }
    throw lastError ?: IllegalStateException("Unable to load OKX candle history")
}
