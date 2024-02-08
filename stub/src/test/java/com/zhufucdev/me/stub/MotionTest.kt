package com.zhufucdev.me.stub

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MotionTest {
    @Test
    fun test_serialization() {
        val expected = Motion(
                "hi I am john cena", mapOf(
                    1 to listOf(SensorMoment(1f, floatArrayOf(4f, 5f, 1f)))
                )
            )
        val text = """{"id":"hi I am john cena","timelines": {"1": [[1.0, 4.0, 5.0, 1.0]]}}"""

        assertEquals("serialization", expected, Json.decodeFromString<Motion>(text))
    }
}