package com.example.hyundai_contest.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedTest {
    @Test fun recentFixKeepsSpeed() {
        assertEquals(60f, freshSpeed(60f, fixAtMs = 10_000, nowMs = 12_000)!!, 0.001f)
    }

    @Test fun staleFixMakesSpeedUnknown() {
        assertNull(freshSpeed(60f, fixAtMs = 10_000, nowMs = 13_001))
    }

    @Test fun noFixIsUnknown() {
        assertNull(freshSpeed(null, fixAtMs = 0, nowMs = 5_000))
    }
}
