package com.example.okxkelly.data.api

object OkxIntervals {
    val supportedBars = listOf(
        "1m", "3m", "5m", "15m", "30m",
        "1H", "2H", "4H", "6H", "12H",
        "1D", "2D", "3D", "1W"
    )

    fun intervalMillis(bar: String): Long? {
        val unit = bar.lastOrNull() ?: return null
        val amount = bar.dropLast(1).toLongOrNull()?.takeIf { it > 0L } ?: return null
        val multiplier = when (unit) {
            'm' -> 60_000L
            'H' -> 60L * 60_000L
            'D' -> 24L * 60L * 60_000L
            'W' -> 7L * 24L * 60L * 60_000L
            else -> return null
        }
        return try {
            Math.multiplyExact(amount, multiplier)
        } catch (_: ArithmeticException) {
            null
        }
    }

    fun livePollingDelayMillis(intervalMillis: Long): Long = when {
        intervalMillis <= 60_000L -> 3_000L
        intervalMillis <= 15L * 60_000L -> 10_000L
        intervalMillis <= 60L * 60_000L -> 30_000L
        else -> 60_000L
    }
}
