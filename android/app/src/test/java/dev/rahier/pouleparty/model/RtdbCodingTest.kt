package dev.rahier.pouleparty.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RtdbCodingTest {

    @Test
    fun `numbers decode from every numeric type RTDB returns`() {
        assertEquals(1.5, rtdbDouble(1.5)!!, 0.0)
        assertEquals(3.0, rtdbDouble(3L)!!, 0.0)
        assertEquals(4.0, rtdbDouble(4)!!, 0.0)
        assertEquals(2.5, rtdbDouble(2.5f)!!, 0.0)
        assertEquals(7L, rtdbLong(7L))
        assertEquals(8L, rtdbLong(8))
        assertEquals(9L, rtdbLong(9.9))
    }

    @Test
    fun `anything else is absent`() {
        assertNull(rtdbDouble("1.0"))
        assertNull(rtdbDouble(null))
        assertNull(rtdbLong(true))
        assertNull(rtdbLong(null))
    }
}
