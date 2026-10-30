package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.SikayetIstegi
import com.sosyaloyun.shared.SikayetSebebi
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun Route.sikayetRotalari(moderasyon: Moderasyon, moderasyonSifresi: String?) {
    oturumGerekli {
        post(ApiYollari.SIKAYETLER) {
            val hata = moderasyon.sikayetEt(call.oturumSahibi.kullaniciId, call.receive<SikayetIstegi>())
            if (hata != null) call.hata(HttpStatusCode.BadRequest, hata) else call.respond(HttpStatusCode.NoContent)
        }
    }

    // Moderasyon sayfası: HTTP Basic (kullanıcı adı herhangi, şifre MODERASYON_SIFRESI). Şifre tanımlı değilse sayfa yok.
    get(ApiYollari.MODERASYON) {
        val sifre = call.moderatorMu(moderasyonSifresi) ?: return@get
        call.respondText(moderasyonSayfasi(moderasyon.acikSikayetler(), formAnahtari(sifre)), ContentType.Text.Html)
    }
    post("${ApiYollari.MODERASYON}/sikayetler/{id}") {
        val sifre = call.moderatorMu(moderasyonSifresi) ?: return@post
        val form = call.receiveParameters()
        // Başka bir siteden gönderilen form şifreyi bilmeden bu anahtarı üretemez.
        if (!MessageDigest.isEqual(form["anahtar"].orEmpty().toByteArray(), formAnahtari(sifre).toByteArray())) {
            return@post call.respond(HttpStatusCode.Forbidden)
        }
        val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val karar = form["karar"]?.let { k -> SikayetDurumu.entries.firstOrNull { it.name == k && it != SikayetDurumu.ACIK } }
        if (id == null || karar == null) return@post call.respond(HttpStatusCode.BadRequest)
        moderasyon.karar(id, karar)
        call.respondRedirect(ApiYollari.MODERASYON)
    }
}

/** Şifre doğruysa onu döner; değilse 401 (ya da sayfa kapalıysa 404) yanıtını verip null döner. */
private suspend fun RoutingCall.moderatorMu(sifre: String?): String? {
    if (sifre.isNullOrEmpty()) {
        respond(HttpStatusCode.NotFound)
        return null
    }
    val gelen = request.headers[HttpHeaders.Authorization]
        ?.takeIf { it.startsWith("Basic ") }
        ?.let { runCatching { String(Base64.getDecoder().decode(it.removePrefix("Basic ").trim())) }.getOrNull() }
        ?.substringAfter(':', "")
    if (gelen == null || !MessageDigest.isEqual(gelen.toByteArray(), sifre.toByteArray())) {
        response.header(HttpHeaders.WWWAuthenticate, "Basic realm=\"moderasyon\", charset=\"UTF-8\"")
        respond(HttpStatusCode.Unauthorized)
        return null
    }
    return sifre
}

private fun formAnahtari(sifre: String): String {
    val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(sifre.toByteArray(), "HmacSHA256")) }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal("moderasyon-formu".toByteArray()))
}

private fun String.html(): String = buildString {
    for (c in this@html) append(
        when (c) {
            '<' -> "&lt;"
            '>' -> "&gt;"
            '&' -> "&amp;"
            '"' -> "&quot;"
            '\'' -> "&#39;"
            else -> c
        },
    )
}

private val SEBEP_ADI = mapOf(
    SikayetSebebi.SPAM to "Spam",
    SikayetSebebi.TACIZ to "Taciz",
    SikayetSebebi.NEFRET_SOYLEMI to "Nefret söylemi",
    SikayetSebebi.UYGUNSUZ_ICERIK to "Uygunsuz içerik",
    SikayetSebebi.ISARETSIZ_SPOILER to "İşaretsiz spoiler",
    SikayetSebebi.SAHTE_HESAP to "Sahte hesap",
    SikayetSebebi.DIGER to "Diğer",
)

private fun moderasyonSayfasi(sikayetler: List<AcikSikayet>, anahtar: String): String = buildString {
    append("<!doctype html><html lang=\"tr\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
    append("<title>Moderasyon</title><style>body{font-family:system-ui,sans-serif;max-width:860px;margin:2rem auto;padding:0 1rem}")
    append("article{border:1px solid #ccc;border-radius:8px;padding:1rem;margin:1rem 0}blockquote{white-space:pre-wrap;background:#f4f4f4;padding:.5rem;margin:.5rem 0}")
    append("form{display:inline}button{margin:.25rem}</style></head><body>")
    append("<h1>Açık şikayetler (${sikayetler.size})</h1>")
    if (sikayetler.isEmpty()) append("<p>Karar bekleyen şikayet yok.</p>")
    for (s in sikayetler) {
        append("<article><p><strong>${SEBEP_ADI.getValue(s.sebep).html()}</strong> · ${if (s.gunlukKaydi) "Günlük kaydı" else "Profil"}")
        append(" · @${s.hedef.orEmpty().html()} · ${s.zaman.toInstant()}</p>")
        append("<p>Şikayet eden: @${s.sikayetEden.orEmpty().html()}</p>")
        s.icerik?.let { append("<blockquote>${it.html()}</blockquote>") }
        s.aciklama?.let { append("<p>Açıklama: ${it.html()}</p>") }
        append("<form method=\"post\" action=\"${ApiYollari.MODERASYON}/sikayetler/${s.id}\"><input type=\"hidden\" name=\"anahtar\" value=\"$anahtar\">")
        for ((karar, etiket) in listOf(
            SikayetDurumu.REDDEDILDI to "Reddet",
            SikayetDurumu.ICERIK_KALDIRILDI to "İçeriği kaldır",
            SikayetDurumu.ASKIYA_ALINDI to "Hesabı askıya al",
            SikayetDurumu.BANLANDI to "Kalıcı ban",
        )) {
            append("<button name=\"karar\" value=\"${karar.name}\">$etiket</button>")
        }
        append("</form></article>")
    }
    append("</body></html>")
}
