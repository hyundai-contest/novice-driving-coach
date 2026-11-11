package com.example.hyundai_contest.demo

import com.example.hyundai_contest.coach.Coach
import com.example.hyundai_contest.model.RecordType
import com.example.hyundai_contest.store.Places
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SyntheticHistoryTest {
    private val end = LocalDateTime.of(2026, 11, 9, 8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val week = 7L * 24 * 3600 * 1000
    private val history = SyntheticHistory.generate(endMs = end)

    private fun summary(fromMs: Long, toMs: Long) =
        Coach.summarize(history.flatMap { it.records }.filter { it.atMs in fromMs until toMs })

    private val firstWeeks = summary(end - 12 * week, end - 9 * week)
    private val lastWeeks = summary(end - 3 * week, end)

    @Test fun sameSeedSameHistory() {
        assertEquals(history, SyntheticHistory.generate(endMs = end))
    }

    @Test fun spansTwelveWeeks() {
        assertTrue(history.first().startMs >= end - 12 * week)
        assertTrue(history.last().endMs <= end)
        assertTrue(history.zipWithNext().all { (a, b) -> a.startMs <= b.startMs })
    }

    @Test fun signalRateImproves() {
        val first = firstWeeks.signalRatePct!!
        val last = lastWeeks.signalRatePct!!
        assertTrue("first=$first last=$last", last >= first + 15)
    }

    @Test fun parkingTakesFewerGearChanges() {
        val first = firstWeeks.parkShifts.average()
        val last = lastWeeks.parkShifts.average()
        assertTrue("first=$first last=$last", last <= first - 2)
    }

    @Test fun forgettingTheSignalOnBecomesRarer() {
        assertTrue("first=${firstWeeks.leftOn} last=${lastWeeks.leftOn}", lastWeeks.leftOn < firstWeeks.leftOn)
    }

    @Test fun cityHallIntersectionStaysTheWeakSpot() {
        val misses = history.flatMap { it.records }
            .filter { it.type == RecordType.TURN_NO_SIGNAL }
            .groupingBy { Places.keyFor(it.lat!!, it.lng!!) }.eachCount()
        assertEquals("시청 사거리", misses.maxByOrNull { it.value }!!.key)
    }

    @Test fun everyTripEndsWithOneParking() {
        assertTrue(history.all { t -> t.records.count { it.type == RecordType.PARKING } == 1 })
    }

    @Test fun recordsLieWithinTheirTrip() {
        assertTrue(history.all { t -> t.records.all { it.atMs in t.startMs..t.endMs } })
    }
}
