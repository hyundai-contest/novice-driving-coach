package com.example.hyundai_contest.coach

import com.example.hyundai_contest.model.Record
import com.example.hyundai_contest.model.RecordType
import kotlin.math.roundToInt

data class TripSummary(
    val signaled: Int, val late: Int, val missed: Int,
    val leftOn: Int, val parkShifts: List<Int>, val leftOpen: Int, val notParked: Int,
    val fastTurns: Int = 0, val fastReverse: Int = 0, val rollingShifts: Int = 0,
    val highBeam: Int = 0, val departUnready: Int = 0,
) {
    val turns get() = signaled + late + missed

    /** Share of turns signalled in time, or null when the trip had no turns to judge. */
    val signalRatePct: Int? get() = if (turns == 0) null else (signaled * 100f / turns).roundToInt()
}

/** Turns records into what a driving instructor would say. Pure, so the wording is testable. */
object Coach {
    private const val HISTORY = 10

    fun summarize(records: List<Record>): TripSummary {
        fun n(t: RecordType) = records.count { it.type == t }
        val parkings = records.filter { it.type == RecordType.PARKING }
        return TripSummary(
            signaled = n(RecordType.TURN_OK), late = n(RecordType.TURN_LATE_SIGNAL), missed = n(RecordType.TURN_NO_SIGNAL),
            leftOn = n(RecordType.SIGNAL_LEFT_ON),
            parkShifts = parkings.mapNotNull { it.detail["shifts"]?.toIntOrNull() },
            leftOpen = n(RecordType.LEFT_OPEN_ON_EXIT), notParked = n(RecordType.EXIT_NOT_PARKED),
            fastTurns = n(RecordType.TURN_FAST), fastReverse = n(RecordType.REVERSE_FAST),
            rollingShifts = parkings.sumOf { it.detail["rollingShifts"]?.toIntOrNull() ?: 0 },
            highBeam = n(RecordType.HIGH_BEAM_LEFT_ON), departUnready = n(RecordType.DEPART_UNREADY),
        )
    }

    /** One thing to work on next time: the most safety-relevant issue of the trip, never a list. */
    fun tip(s: TripSummary): String = when {
        s.notParked > 0 -> "내리기 전에 기어가 P인지 먼저 확인하세요."
        s.departUnready > 0 -> "출발하기 전에 안전벨트와 문을 먼저 확인하세요."
        s.missed > 0 -> "회전 ${s.turns}번 중 ${s.missed}번은 깜빡이가 없었어요. 돌기 30미터 앞에서 미리 켜 보세요."
        s.fastReverse > 0 -> "후진이 빨랐어요. 브레이크에 발을 올린 채 걷는 속도로 움직여 보세요."
        s.fastTurns > 0 -> "회전할 때 속도가 빨랐어요. 핸들을 돌리기 전에 먼저 속도를 줄여 보세요."
        s.leftOn > 0 -> "차선을 바꾼 뒤에도 깜빡이가 켜져 있었어요. 변경이 끝나면 레버를 되돌려 주세요."
        s.highBeam > 0 -> "상향등이 켜진 채 달렸어요. 마주 오는 차가 있으면 레버를 당겨 꺼 주세요."
        s.late > 0 -> "깜빡이가 핸들을 돌리는 순간에 켜졌어요. 조금 더 일찍, 30미터 앞에서 켜 보세요."
        s.rollingShifts > 0 -> "차가 완전히 멈춘 뒤에 기어를 바꿔 보세요."
        s.leftOpen > 0 -> "내릴 때 창문과 전조등을 한 번 더 확인하세요."
        s.turns > 0 || s.parkShifts.isNotEmpty() -> "이번 주행은 놓친 조작이 없었어요. 지금처럼만 하면 됩니다."
        else -> "아직 코칭할 조작이 기록되지 않았어요."
    }

    /** Spoken right after parking: this attempt against the driver's own recent average, not against others. */
    fun parkingFeedback(shifts: Int, previous: List<Int>): String {
        val recent = previous.takeLast(HISTORY)
        if (recent.isEmpty()) return "주차 완료. 기어를 ${shifts}번 바꿨어요"
        val usual = recent.average().roundToInt()
        return when {
            shifts < usual -> "주차 완료. 기어를 ${shifts}번 바꿨어요. 평소 ${usual}번보다 줄었어요"
            shifts == usual -> "주차 완료. 기어를 ${shifts}번 바꿨어요. 평소와 같아요"
            else -> "주차 완료. 기어를 ${shifts}번 바꿨어요. 평소는 ${usual}번이에요"
        }
    }
}
