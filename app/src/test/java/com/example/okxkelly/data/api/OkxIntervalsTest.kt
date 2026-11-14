package com.example.okxkelly.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OkxIntervalsTest {
    @Test
    fun supportsCommonFixedOkxIntervals() {
        assertEquals(60_000L, OkxIntervals.intervalMillis("1m"))
        assertEquals(30L * 60_000L, OkxIntervals.intervalMillis("30m"))
        assertEquals(4L * 60L * 60_000L, OkxIntervals.intervalMillis("4H"))
        assertEquals(24L * 60L * 60_000L, OkxIntervals.intervalMillis("1D"))
        assertEquals(7L * 24L * 60L * 60_000L, OkxIntervals.intervalMillis("1W"))
        assertNull(OkxIntervals.intervalMillis("1M"))
        assertNull(OkxIntervals.intervalMillis("0m"))
    }

    @Test
    fun usesLongerPollingForSlowerTimeframes() {
        assertEquals(3_000L, OkxIntervals.livePollingDelayMillis(60_000L))
        assertEquals(10_000L, OkxIntervals.livePollingDelayMillis(5L * 60_000L))
        assertEquals(30_000L, OkxIntervals.livePollingDelayMillis(60L * 60_000L))
        assertEquals(60_000L, OkxIntervals.livePollingDelayMillis(24L * 60L * 60L * 1_000L))
        assertTrue(OkxIntervals.supportedBars.contains("1W"))
    }
}
