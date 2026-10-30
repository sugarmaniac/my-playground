package com.sosyaloyun.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.slf4j.LoggerFactory

/** Moderatöre giden e-postalar (yeni şikayet). */
interface EpostaGonderici {
    suspend fun gonder(konu: String, metin: String)
}

/** Resend HTTP API'si; anahtar `RESEND_API_KEY`, gönderen `EPOSTA_GONDEREN`, alıcı `MODERASYON_EPOSTASI` ortam değişkenlerinden. */
class ResendEpostaGonderici(
    private val anahtar: String,
    private val gonderen: String,
    private val alici: String,
    private val http: HttpClient = HttpClient(CIO),
) : EpostaGonderici {
    override suspend fun gonder(konu: String, metin: String) {
        val govde = buildJsonObject {
            put("from", gonderen)
            putJsonArray("to") { add(alici) }
            put("subject", konu)
            put("text", metin)
        }
        val yanit = http.post("https://api.resend.com/emails") {
            bearerAuth(anahtar)
            contentType(ContentType.Application.Json)
            setBody(govde.toString())
        }
        check(yanit.status.isSuccess()) { "E-posta gönderilemedi: ${yanit.status} ${yanit.bodyAsText().take(300)}" }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ResendEpostaGonderici::class.java)

        fun ortamdan(): ResendEpostaGonderici? {
            fun ortam(ad: String) = System.getenv(ad).orEmpty().trim()
            val anahtar = ortam("RESEND_API_KEY")
            val alici = ortam("MODERASYON_EPOSTASI")
            if (anahtar.isEmpty() || alici.isEmpty()) {
                log.warn("RESEND_API_KEY/MODERASYON_EPOSTASI tanımlı değil; yeni şikayetler e-postayla bildirilmeyecek.")
                return null
            }
            return ResendEpostaGonderici(anahtar, ortam("EPOSTA_GONDEREN").ifEmpty { "Sosyal Oyun <onboarding@resend.dev>" }, alici)
        }
    }
}
