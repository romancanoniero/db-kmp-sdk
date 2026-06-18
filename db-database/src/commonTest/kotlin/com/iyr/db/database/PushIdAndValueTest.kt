package com.iyr.db.database

import com.iyr.db.database.internal.PushId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PushIdAndValueTest {

    @Test
    fun pushIdsAreLengthDeterministic() {
        val id = PushId.generate()
        assertEquals(20, id.length)
    }

    @Test
    fun pushIdsAreLexicographicallyOrderedByTime() {
        val earlier = PushId.generate(now = 1_000L)
        val later = PushId.generate(now = 2_000L)
        assertTrue(earlier < later, "Un push-id más nuevo debe ordenar después que uno más viejo")
    }

    @Test
    fun anyToJsonElementRoundTripsPrimitivesAndMaps() {
        val element = mapOf(
            "name" to "diana",
            "age" to 33L,
            "active" to true,
            "loc" to listOf(1.0, 2.0),
        ).toJsonElement()
        val back = element.toKotlin() as Map<*, *>
        assertEquals("diana", back["name"])
        assertEquals(true, back["active"])
    }
}
