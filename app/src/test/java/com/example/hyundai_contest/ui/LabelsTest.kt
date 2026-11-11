package com.example.hyundai_contest.ui

import com.example.hyundai_contest.model.Record
import com.example.hyundai_contest.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Test

class LabelsTest {
    private fun late(leadMs: String) =
        Labels.detail(Record(RecordType.TURN_LATE_SIGNAL, 0, null, null, mapOf("side" to "LEFT", "leadMs" to leadMs)))

    @Test fun lateSignalAfterTurnStartSaysAfter() {
        assertEquals("왼쪽 회전 · 회전 시작 0.5초 뒤에 켬", late("-500"))
    }

    @Test fun lateSignalJustBeforeTurnStartSaysBefore() {
        assertEquals("왼쪽 회전 · 회전 시작 0.5초 전에 켬", late("500"))
    }
}
