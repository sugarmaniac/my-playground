package com.sosyaloyun.server

import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.shared.ApiJson
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.SaglikYaniti
import com.sosyaloyun.shared.SunucuDurumu
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class SaglikTest {
    @Test
    fun ayarYokkenServerAyaktaOldugunuVeSaatiniSoyler() = apiTesti {
        saat.ilerlet(3.hours)

        val yanit = client.get(ApiYollari.SAGLIK)

        assertEquals(HttpStatusCode.OK, yanit.status)
        assertTrue(yanit.contentType()!!.match(ContentType.Application.Json))
        assertEquals(
            SaglikYaniti(durum = SunucuDurumu.AYAKTA, apiSurumu = SaglikYaniti.API_SURUMU, sunucuZamani = saat.simdi),
            ApiJson.decodeFromString(SaglikYaniti.serializer(), yanit.bodyAsText()),
        )
    }

    @Test
    fun bakimModuAyariAcilincaServerBakimdaOldugunuSoyler() = apiTesti {
        ayarlar.yaz(Ayar.BakimModu, true)

        val yanit = ApiJson.decodeFromString(SaglikYaniti.serializer(), client.get(ApiYollari.SAGLIK).bodyAsText())

        assertEquals(SunucuDurumu.BAKIMDA, yanit.durum)
    }
}
