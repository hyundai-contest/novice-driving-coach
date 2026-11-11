package com.example.hyundai_contest.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesTest {
    private val cityHall = Places.DEMO.single { it.name == "시청 사거리" }

    @Test fun nearPlaceGetsName() {
        // ~50 m north (0.00045° latitude)
        assertEquals("시청 사거리", Places.keyFor(cityHall.lat + 0.00045, cityHall.lng))
    }

    @Test fun labelHidesGridKey() {
        assertEquals("이름 없는 장소", Places.label(37.6000, 127.1000))
        assertEquals("시청 사거리", Places.label(cityHall.lat, cityHall.lng))
    }

    @Test fun farPointGetsGridKey() {
        val a = Places.keyFor(37.6000, 127.1000)
        val b = Places.keyFor(37.60004, 127.10004)
        assertTrue(a.startsWith("격자 "))
        assertEquals(a, b)
    }
}
