package com.example.hyundai_contest.detect

import com.example.hyundai_contest.model.CarState
import com.example.hyundai_contest.model.Gear
import com.example.hyundai_contest.model.RecordType
import com.example.hyundai_contest.model.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Driving state at [atMs]; left is negative steering (AAOS convention). */
fun d(atMs: Long, steer: Float = 0f, signal: Side? = null, kph: Float? = 20f) =
    CarState(atMs = atMs, gear = Gear.DRIVE, speedKph = kph, steeringDeg = steer, turnSignal = signal)

/** Turn that starts at 4000 ms, is held until 6000 ms and ends at 7000 ms. */
fun turn(steer: Float, signalAt: Map<Long, Side> = emptyMap(), kph: Float? = 20f): Array<CarState> {
    val times = listOf(1000L, 2000L, 3000L, 4000L, 4500L, 5000L, 6000L, 7000L)
    var signal: Side? = null
    return times.map { t ->
        signalAt[t]?.let { signal = it }
        d(t, steer = if (t in 4000L..6000L) steer else 0f, signal = signal, kph = kph)
    }.toTypedArray()
}

class DetectionEngineTurnTest {

    @Test fun leftTurnWithoutSignalIsNoSignal() {
        val r = records(drive(parked, *turn(-120f))).single()
        assertEquals(RecordType.TURN_NO_SIGNAL, r.record.type)
        assertEquals("LEFT", r.record.detail["side"])
        assertEquals(4000L, r.record.atMs)
        assertNull(r.announce)
    }

    @Test fun signalThreeSecondsBeforeIsCountedAsDoneRight() {
        val r = records(drive(parked, *turn(-120f, mapOf(1000L to Side.LEFT)))).single()
        assertEquals(RecordType.TURN_OK, r.record.type)
        assertNull(r.announce)
    }

    @Test fun signalAfterTurnStartIsLate() {
        val r = records(drive(parked, *turn(-120f, mapOf(4500L to Side.LEFT)))).single()
        assertEquals(RecordType.TURN_LATE_SIGNAL, r.record.type)
        assertEquals("-500", r.record.detail["leadMs"])
    }

    @Test fun wrongSideSignalIsNoSignal() {
        val r = records(drive(parked, *turn(120f, mapOf(1000L to Side.LEFT)))).single()
        assertEquals(RecordType.TURN_NO_SIGNAL, r.record.type)
        assertEquals("RIGHT", r.record.detail["side"])
    }

    @Test fun shortSteeringSpikeIsNotATurn() {
        val ev = drive(parked, d(1000), d(4000, steer = -120f), d(4500), d(5000))
        assertTrue(records(ev).isEmpty())
    }

    @Test fun turnAboveSpeedIgnored() {
        assertTrue(records(drive(parked, *turn(-120f, kph = 60f))).isEmpty())
    }

    @Test fun turnWithUnknownSpeedIsJudged() {
        assertEquals(1, records(drive(parked, *turn(-120f, kph = null))).size)
    }
}
