package com.example.hyundai_contest.detect

import com.example.hyundai_contest.model.Seat
import com.example.hyundai_contest.model.Side

/** Exact spoken copy for the moment something is noticed. Short enough to hear while driving. */
object Announcements {
    const val EXIT_NOT_PARKED = "기어가 P가 아니에요. P에 놓고 내리세요"
    const val REVERSE_FAST = "후진이 빨라요. 천천히 가세요"
    const val HIGH_BEAM_LEFT_ON = "상향등이 켜져 있어요"

    /** The belt is not spoken: the car already chimes for it. */
    fun departDoors(doors: List<Seat>): String? =
        if (doors.isEmpty()) null else doors.joinToString(", ") { seatName(it) } + " 문이 열려 있어요"

    fun seatName(seat: Seat): String = when (seat) {
        Seat.FL -> "운전석"
        Seat.FR -> "조수석"
        Seat.RL -> "왼쪽 뒷좌석"
        Seat.RR -> "오른쪽 뒷좌석"
    }

    fun signalLeftOn(side: Side): String =
        if (side == Side.LEFT) "왼쪽 깜빡이가 켜져 있어요" else "오른쪽 깜빡이가 켜져 있어요"

    fun leftOpenOnExit(windows: List<Seat>, headlamp: Boolean): String? {
        val parts = buildList {
            if (windows.isNotEmpty()) add(windows.joinToString(", ") { seatName(it) } + " 창문이 열려 있어요")
            if (headlamp) add("전조등이 켜져 있어요")
        }
        return parts.joinToString(". ").ifEmpty { null }
    }
}
