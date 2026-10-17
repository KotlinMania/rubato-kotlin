// port-lint: tests rubato/src/interpolation.rs
package io.github.kotlinmania.rubato

import kotlin.test.Test
import kotlin.test.assertEquals

class InterpolationTest {
    @Test
    fun getNearest2() {
        val t = 5.9
        val times = Array(2) { IndexSubindex(0, 0) }
        getNearestTimes2(t, 8, times)
        assertEquals(IndexSubindex(5, 7), times[0])
        assertEquals(IndexSubindex(6, 0), times[1])
    }

    @Test
    fun getNearest4() {
        val t = 5.9
        val times = Array(4) { IndexSubindex(0, 0) }
        getNearestTimes4(t, 8, times)
        assertEquals(IndexSubindex(5, 6), times[0])
        assertEquals(IndexSubindex(5, 7), times[1])
        assertEquals(IndexSubindex(6, 0), times[2])
        assertEquals(IndexSubindex(6, 1), times[3])
    }

    @Test
    fun getNearest3() {
        val t = 5.9
        val times = Array(3) { IndexSubindex(0, 0) }
        getNearestTimes3(t, 8, times)
        assertEquals(IndexSubindex(5, 7), times[0])
        assertEquals(IndexSubindex(6, 0), times[1])
        assertEquals(IndexSubindex(6, 1), times[2])
    }

    @Test
    fun getNearest4Neg() {
        val t = -5.999
        val times = Array(4) { IndexSubindex(0, 0) }
        getNearestTimes4(t, 8, times)
        assertEquals(IndexSubindex(-7, 7), times[0])
        assertEquals(IndexSubindex(-6, 0), times[1])
        assertEquals(IndexSubindex(-6, 1), times[2])
        assertEquals(IndexSubindex(-6, 2), times[3])
    }

    @Test
    fun getNearest4Zero() {
        val t = -0.00001
        val times = Array(4) { IndexSubindex(0, 0) }
        getNearestTimes4(t, 8, times)
        assertEquals(IndexSubindex(-1, 6), times[0])
        assertEquals(IndexSubindex(-1, 7), times[1])
        assertEquals(IndexSubindex(0, 0), times[2])
        assertEquals(IndexSubindex(0, 1), times[3])
    }

    @Test
    fun getNearestSingle() {
        val t = 5.5
        val time = getNearestTime(t, 8)
        assertEquals(IndexSubindex(5, 4), time)
    }
}
