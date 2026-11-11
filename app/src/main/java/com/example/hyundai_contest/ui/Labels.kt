package com.example.hyundai_contest.ui

import com.example.hyundai_contest.detect.Announcements
import com.example.hyundai_contest.model.Record
import com.example.hyundai_contest.model.RecordType
import com.example.hyundai_contest.model.Seat
import com.example.hyundai_contest.signal.Domain
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Names and details for records, in the words a driving instructor would use. */
object Labels {
    /** Things to work on. TURN_OK and PARKING are progress measures, not misses. */
    val MISSES = setOf(
        RecordType.TURN_NO_SIGNAL, RecordType.TURN_LATE_SIGNAL, RecordType.SIGNAL_LEFT_ON,
        RecordType.LEFT_OPEN_ON_EXIT, RecordType.EXIT_NOT_PARKED,
    )

    fun name(type: RecordType): String = when (type) {
        RecordType.TURN_OK -> "깜빡이 켜고 회전"
        RecordType.TURN_NO_SIGNAL -> "깜빡이 없이 회전"
        RecordType.TURN_LATE_SIGNAL -> "깜빡이 늦게 켬"
        RecordType.SIGNAL_LEFT_ON -> "깜빡이 켜둔 채 주행"
        RecordType.PARKING -> "후진 주차"
        RecordType.LEFT_OPEN_ON_EXIT -> "열어둔 채 하차"
        RecordType.EXIT_NOT_PARKED -> "P단이 아닌데 문 열림"
    }

    /** What the coach says about it on the driving screen: praise or the one thing to do next. */
    fun coach(type: RecordType): String = when (type) {
        RecordType.TURN_OK -> "좋아요!"
        RecordType.TURN_NO_SIGNAL -> "다음엔 깜빡이 먼저"
        RecordType.TURN_LATE_SIGNAL -> "조금만 더 일찍 켜요"
        RecordType.SIGNAL_LEFT_ON -> "깜빡이를 꺼주세요"
        RecordType.PARKING -> "주차 완료"
        RecordType.LEFT_OPEN_ON_EXIT -> "내리기 전에 확인"
        RecordType.EXIT_NOT_PARKED -> "P에 놓고 내리세요"
    }

    /** Vehicle signals a record type depends on; if any is missing the metric is "not measurable". */
    fun needs(type: RecordType): Set<Domain> = when (type) {
        RecordType.SIGNAL_LEFT_ON -> setOf(Domain.TurnSignal)
        RecordType.TURN_OK, RecordType.TURN_NO_SIGNAL, RecordType.TURN_LATE_SIGNAL -> setOf(Domain.TurnSignal, Domain.Steering)
        RecordType.PARKING -> setOf(Domain.Gear)
        RecordType.LEFT_OPEN_ON_EXIT -> setOf(Domain.Door, Domain.Window, Domain.Gear)
        RecordType.EXIT_NOT_PARKED -> setOf(Domain.Door, Domain.Gear)
    }

    private fun side(v: String?) = if (v == "LEFT") "왼쪽" else "오른쪽"

    private fun seconds(ms: String?) = ms?.toLongOrNull()?.let { "%.1f초".format(Locale.KOREAN, it / 1000.0) } ?: "-"

    // leadMs = turn start − first signal on: negative means after the turn began, positive means just before it.
    private fun lateText(side: String, leadMs: Long): String {
        val s = seconds(kotlin.math.abs(leadMs).toString())
        return if (leadMs < 0) "$side 회전 · 회전 시작 $s 뒤에 켬" else "$side 회전 · 회전 시작 $s 전에 켬"
    }

    fun detail(r: Record): String {
        val d = r.detail
        return when (r.type) {
            RecordType.TURN_OK -> "${side(d["side"])} 회전 · 미리 켬"
            RecordType.TURN_NO_SIGNAL -> "${side(d["side"])} 회전 · 깜빡이 없음"
            RecordType.TURN_LATE_SIGNAL -> lateText(side(d["side"]), d["leadMs"]?.toLongOrNull() ?: 0)
            RecordType.SIGNAL_LEFT_ON -> "${side(d["side"])} 깜빡이 · ${seconds(d["heldMs"])} 동안 켜짐"
            RecordType.PARKING ->
                "기어 변경 ${d["shifts"]}번 · 핸들 되감기 ${d["steerReversals"]}번 · ${(d["durationMs"]?.toLongOrNull() ?: 0) / 1000}초"
            RecordType.LEFT_OPEN_ON_EXIT -> buildList {
                d["windows"]?.split(',')?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.let { seats ->
                    add(seats.joinToString(", ") { Announcements.seatName(Seat.valueOf(it)) } + " 창문 열림")
                }
                if (d["headlamp"] == "true") add("전조등 켜짐")
            }.joinToString(" · ")
            RecordType.EXIT_NOT_PARKED -> "기어 ${d["gear"]?.take(1) ?: "-"}에서 운전석 문 열림"
        }
    }

    private val zone = ZoneId.systemDefault()
    private val dateTime = DateTimeFormatter.ofPattern("M/d (E) HH:mm", Locale.KOREAN)
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.KOREAN)

    fun dateTime(ms: Long): String = dateTime.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun time(ms: Long): String = time.format(Instant.ofEpochMilli(ms).atZone(zone))
}
