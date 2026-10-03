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
        @Query("limit") limit: Int = 100,
        @Query("after") after: String? = null
    ): OkxCandleResponse
}

fun parseOkxCandle(row: List<String>): Candle? {
    if (row.size < 5) return null
    return try {
        Candle(
            timestamp = row[0].toLongOrNull() ?: 0L,
            open = row[1].toDoubleOrNull() ?: 0.0,
            high = row[2].toDoubleOrNull() ?: 0.0,
            low = row[3].toDoubleOrNull() ?: 0.0,
            close = row[4].toDoubleOrNull() ?: 0.0,
            vol = if (row.size > 5) row[5].toDoubleOrNull() ?: 0.0 else 0.0,
            volCcy = if (row.size > 6) row[6].toDoubleOrNull() ?: 0.0 else 0.0,
            volCcyQuote = if (row.size > 7) row[7].toDoubleOrNull() ?: 0.0 else 0.0,
            confirm = if (row.size > 8) row[8] == "1" else false
        )
    } catch (_: Exception) {
        null
    }
}
