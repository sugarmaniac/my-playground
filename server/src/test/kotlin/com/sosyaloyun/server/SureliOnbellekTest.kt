package com.sosyaloyun.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource
import kotlinx.coroutines.test.runTest

class SureliOnbellekTest {
    @Test
    fun sureDolanaKadarSaklananDonerSonraYenidenUretilir() = runTest {
        val zaman = TestTimeSource()
        val onbellek = SureliOnbellek<String, Int>(15.minutes, zaman = zaman)
        var uretim = 0

        assertEquals(1, onbellek.getir("a") { ++uretim })
        zaman += 14.minutes
        assertEquals(1, onbellek.getir("a") { ++uretim })
        zaman += 2.minutes
        assertEquals(2, onbellek.getir("a") { ++uretim })
    }

    @Test
    fun nullSaklanmazSuresizOnbellekHerSeferUretir() = runTest {
        val onbellek = SureliOnbellek<String, Int>(15.minutes)
        var uretim = 0
        assertEquals(null, onbellek.getir("yok") { uretim++; null })
        assertEquals(null, onbellek.getir("yok") { uretim++; null })
        assertEquals(2, uretim)

        val kapali = SureliOnbellek<String, Int>(Duration.ZERO)
        kapali.getir("a") { ++uretim }
        assertEquals(4, kapali.getir("a") { ++uretim })
    }
}
