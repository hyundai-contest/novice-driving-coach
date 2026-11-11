package com.example.hyundai_contest.coach

import com.example.hyundai_contest.model.Record
import com.example.hyundai_contest.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachTest {
    private fun r(type: RecordType, vararg detail: Pair<String, String>) = Record(type, 0, null, null, mapOf(*detail))

    @Test fun summaryCountsTurnsAndRate() {
        val s = Coach.summarize(
            listOf(
                r(RecordType.TURN_OK), r(RecordType.TURN_OK), r(RecordType.TURN_LATE_SIGNAL), r(RecordType.TURN_NO_SIGNAL),
                r(RecordType.PARKING, "shifts" to "5"), r(RecordType.SIGNAL_LEFT_ON),
            ),
        )
        assertEquals(4, s.turns)
        assertEquals(50, s.signalRatePct)
        assertEquals(listOf(5), s.parkShifts)
        assertEquals(1, s.leftOn)
    }

    @Test fun rateIsUnknownWithoutTurns() {
        assertNull(Coach.summarize(emptyList()).signalRatePct)
    }

    @Test fun tipPicksTheMostSafetyRelevantIssue() {
        val both = Coach.summarize(listOf(r(RecordType.TURN_NO_SIGNAL), r(RecordType.EXIT_NOT_PARKED), r(RecordType.LEFT_OPEN_ON_EXIT)))
        assertTrue(Coach.tip(both).contains("P"))
        assertTrue(Coach.tip(Coach.summarize(listOf(r(RecordType.TURN_OK), r(RecordType.TURN_NO_SIGNAL)))).contains("회전 2번 중 1번"))
        assertTrue(Coach.tip(Coach.summarize(listOf(r(RecordType.TURN_OK)))).contains("놓친 조작이 없었어요"))
    }

    @Test fun parkingFeedbackComparesWithOwnAverage() {
        assertEquals("주차 완료. 기어를 4번 바꿨어요", Coach.parkingFeedback(4, emptyList()))
        assertEquals("주차 완료. 기어를 3번 바꿨어요. 평소 6번보다 줄었어요", Coach.parkingFeedback(3, listOf(7, 6, 5)))
        assertEquals("주차 완료. 기어를 6번 바꿨어요. 평소와 같아요", Coach.parkingFeedback(6, listOf(7, 6, 5)))
        assertEquals("주차 완료. 기어를 8번 바꿨어요. 평소는 6번이에요", Coach.parkingFeedback(8, listOf(7, 6, 5)))
    }

    @Test fun parkingAverageUsesOnlyTheLastTen() {
        // Twenty old attempts at 9, then ten at 3: "usual" is 3, so 3 is not an improvement.
        assertEquals("주차 완료. 기어를 3번 바꿨어요. 평소와 같아요", Coach.parkingFeedback(3, List(20) { 9 } + List(10) { 3 }))
    }
}
