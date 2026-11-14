package com.example.okxkelly.data.api

import com.example.okxkelly.data.model.Candle
import com.example.okxkelly.data.model.OkxCandleResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OkxApiService {
    @GET("api/v5/market/candles")
    suspend fun getCandles(
        @Query("instId") instId: String,
        @Query("bar") bar: String,
        @Query("limit") limit: Int = 100
    ): OkxCandleResponse

    @GET("api/v5/market/history-candles")
    suspend fun getHistoryCandles(
        @Query("instId") instId: String,
        @Query("bar") bar: String,
        @Query("limit") limit: Int = 300,
        @Query("after") after: String? = null
    ): OkxCandleResponse
}

fun parseOkxCandle(row: List<String>): Candle? {
    if (row.size < 9) return null
    return try {
        val timestamp = row[0].toLongOrNull() ?: return null
        val open = row[1].toDoubleOrNull() ?: return null
        val high = row[2].toDoubleOrNull() ?: return null
        val low = row[3].toDoubleOrNull() ?: return null
        val close = row[4].toDoubleOrNull() ?: return null
        if (timestamp <= 0L || listOf(open, high, low, close).any { !it.isFinite() || it <= 0.0 }) return null
        if (high < maxOf(open, close, low) || low > minOf(open, close, high)) return null
        val vol = row[5].toDoubleOrNull() ?: return null
        val volCcy = row[6].toDoubleOrNull() ?: return null
        val volCcyQuote = row[7].toDoubleOrNull() ?: return null
        if (listOf(vol, volCcy, volCcyQuote).any { !it.isFinite() || it < 0.0 }) return null
        Candle(
            timestamp = timestamp,
            open = open,
            high = high,
            low = low,
            close = close,
            vol = vol,
            volCcy = volCcy,
            volCcyQuote = volCcyQuote,
            confirm = row[8] == "1"
        )
    } catch (_: Exception) {
        null
    }
}
