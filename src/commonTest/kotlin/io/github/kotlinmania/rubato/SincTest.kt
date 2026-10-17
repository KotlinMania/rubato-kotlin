// port-lint: tests rubato/src/sinc.rs
package io.github.kotlinmania.rubato

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class SincTest {
    @Test
    fun sincs() {
        val sincs = makeSincs(32, 8, 0.9f, WindowFunction.Blackman, F64Sample)
        assertTrue(abs(sincs[7][16] - 1.0) < 0.2)
        var sum = 0.0
        for (v in sincs) {
            for (elem in v) {
                sum += elem
            }
        }
        assertTrue(abs(sum - 8.0) < 0.00001)
    }
}
