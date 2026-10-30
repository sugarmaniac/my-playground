package com.sosyaloyun.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class SaglikYanitiTest {
    @Test
    fun jsonGidisDonusuAyniYanitiVerir() {
        val yanit = SaglikYaniti(
            durum = SunucuDurumu.BAKIMDA,
            apiSurumu = SaglikYaniti.API_SURUMU,
            sunucuZamani = Instant.parse("2026-10-29T12:00:00Z"),
        )

        val json = ApiJson.encodeToString(SaglikYaniti.serializer(), yanit)

        assertEquals("""{"durum":"bakimda","apiSurumu":1,"sunucuZamani":"2026-10-29T12:00:00Z"}""", json)
        assertEquals(yanit, ApiJson.decodeFromString(SaglikYaniti.serializer(), json))
    }
}
