package com.example.okxkelly.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OkxCandleParserTest {
    @Test fun parsesConfirmedOkxCandleFields() {
        val candle = parseOkxCandle(listOf("1730000000000", "67000", "67200", "66900", "67100", "12.3", "12.3", "824000", "1"))
        requireNotNull(candle)
        assertEquals(1730000000000L, candle.timestamp)
        assertEquals(67000.0, candle.open, 0.0)
        assertEquals(67200.0, candle.high, 0.0)
        assertEquals(66900.0, candle.low, 0.0)
        assertEquals(67100.0, candle.close, 0.0)
        assertTrue(candle.confirm)
    }

    @Test fun rejectsMalformedRowsAndKeepsUnconfirmedFlag() {
        assertNull(parseOkxCandle(listOf("1", "2", "3")))
        assertNull(parseOkxCandle(listOf("1730000000000", "67000", "66000", "66900", "67100", "0", "0", "0", "1")))
        val candle = parseOkxCandle(listOf("1730000000000", "67000", "67200", "66900", "67100", "0", "0", "0", "0"))
        requireNotNull(candle)
        assertFalse(candle.confirm)
    }
}
