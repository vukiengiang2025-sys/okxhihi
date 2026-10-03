package com.example.okxkelly.data.model

import kotlinx.serialization.Serializable

data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val vol: Double,
    val volCcy: Double,
    val volCcyQuote: Double,
    val confirm: Boolean
)

data class Trade(
    val type: String = "BUY",
    val entryPrice: Double,
    var exitPrice: Double? = null,
    val entryTime: Long,
    var exitTime: Long? = null,
    var profitRatio: Double? = null,
    var won: Boolean? = null
)

data class SimMetrics(
    val totalTrades: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val averageWinRatio: Double,
    val averageLossRatio: Double,
    val b: Double, // Reward to Risk ratio
    val kellyFraction: Double // Suggested Kelly fraction (f*)
)

data class CapitalPoint(
    val time: String,
    val capital: Double
)

@Serializable
data class OkxCandleResponse(
    val code: String,
    val msg: String? = null,
    val data: List<List<String>> = emptyList()
)

// Gemini API data classes
@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null
)
