package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.KuratorSartTuru
import com.sosyaloyun.shared.SikayetSebebi
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
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

/**
 * Ekibin yönetim paneli (#46): aktif faz ve moderasyon. HTTP Basic (kullanıcı adı herhangi, şifre
 * MODERASYON_SIFRESI); şifre tanımlı değilse panel yok. Formlar şifreden türetilen bir anahtar taşır.
 */
fun Route.yonetimRotalari(moderasyon: Moderasyon, fazlar: Fazlar, kuratorler: Kuratorler, sifre: String?) {
    get(ApiYollari.MODERASYON) { call.respondRedirect(ApiYollari.YONETIM) }

    get(ApiYollari.YONETIM) {
        val dogru = call.yoneticiMi(sifre) ?: return@get
        val aktif = fazlar.aktif()
        // Faz 2 bölümleri yalnızca faz açıkken gösterilir; veri yine tutulur.
        val kuratorBasvurulari = if (aktif.kapsar(Faz.FAZ_2)) kuratorler.bekleyenler() else null
        call.respondText(yonetimSayfasi(aktif, moderasyon.acikSikayetler(), kuratorBasvurulari, formAnahtari(dogru)), ContentType.Text.Html)
    }
    post("${ApiYollari.YONETIM}/kurator/{id}") {
        val form = call.yonetimFormu(sifre) ?: return@post
        val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val onay = when (form["karar"]) {
            "ONAYLA" -> true
            "REDDET" -> false
            else -> null
        }
        if (id == null || onay == null) return@post call.respond(HttpStatusCode.BadRequest)
        kuratorler.karar(id, onay)
        call.respondRedirect(ApiYollari.YONETIM)
    }
    post("${ApiYollari.YONETIM}/faz") {
        val form = call.yonetimFormu(sifre) ?: return@post
        val faz = form["faz"]?.toIntOrNull()?.let { s -> Faz.entries.firstOrNull { it.seviye == s } }
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        fazlar.degistir(faz)
        call.respondRedirect(ApiYollari.YONETIM)
    }
    post("${ApiYollari.YONETIM}/sikayetler/{id}") {
        val form = call.yonetimFormu(sifre) ?: return@post
        val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val karar = form["karar"]?.let { k -> SikayetDurumu.entries.firstOrNull { it.name == k && it != SikayetDurumu.ACIK } }
        if (id == null || karar == null) return@post call.respond(HttpStatusCode.BadRequest)
        moderasyon.karar(id, karar)
        call.respondRedirect(ApiYollari.YONETIM)
    }
}

/** Şifre ve form anahtarı doğruysa formu döner; değilse yanıtı verip null döner. */
internal suspend fun RoutingCall.yonetimFormu(sifre: String?): Parameters? {
    val dogru = yoneticiMi(sifre) ?: return null
    val form = receiveParameters()
    // Başka bir siteden gönderilen form şifreyi bilmeden bu anahtarı üretemez.
    if (!MessageDigest.isEqual(form["anahtar"].orEmpty().toByteArray(), formAnahtari(dogru).toByteArray())) {
        respond(HttpStatusCode.Forbidden)
        return null
    }
    return form
}

/** Şifre doğruysa onu döner; değilse 401 (ya da panel kapalıysa 404) yanıtını verip null döner. */
private suspend fun RoutingCall.yoneticiMi(sifre: String?): String? {
    if (sifre.isNullOrEmpty()) {
        respond(HttpStatusCode.NotFound)
        return null
    }
    val gelen = request.headers[HttpHeaders.Authorization]
        ?.takeIf { it.startsWith("Basic ") }
        ?.let { runCatching { String(Base64.getDecoder().decode(it.removePrefix("Basic ").trim())) }.getOrNull() }
        ?.substringAfter(':', "")
    if (gelen == null || !MessageDigest.isEqual(gelen.toByteArray(), sifre.toByteArray())) {
        response.header(HttpHeaders.WWWAuthenticate, "Basic realm=\"yonetim\", charset=\"UTF-8\"")
        respond(HttpStatusCode.Unauthorized)
        return null
    }
    return sifre
}

private fun formAnahtari(sifre: String): String {
    val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(sifre.toByteArray(), "HmacSHA256")) }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal("moderasyon-formu".toByteArray()))
}

internal fun String.html(): String = buildString {
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

private val HEDEF_ADI = mapOf(SikayetHedefi.KAYIT to "Günlük kaydı", SikayetHedefi.PROFIL to "Profil", SikayetHedefi.YORUM to "Yorum")

private val FAZ_ACIKLAMASI = mapOf(
    Faz.FAZ_1 to "Yalnızca MVP",
    Faz.FAZ_2 to "MVP + Faz 2 (yorumlar, listeler, küratörler, puan geçmişi, gizli profil, itirazlar…)",
    Faz.FAZ_3 to "MVP + Faz 2 + Faz 3 (istatistikler, yıllık özet, öneriler, Xbox/PlayStation, webde giriş)",
)

private val SART_ADI = mapOf(
    KuratorSartTuru.INCELEME to "inceleme",
    KuratorSartTuru.TAKIPCI to "takipçi",
    KuratorSartTuru.HESAP_YASI_GUN to "gün",
)

private fun yonetimSayfasi(aktifFaz: Faz, sikayetler: List<AcikSikayet>, kuratorBasvurulari: List<BekleyenKuratorBasvurusu>?, anahtar: String): String = buildString {
    append("<!doctype html><html lang=\"tr\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
    append("<title>Yönetim</title><style>body{font-family:system-ui,sans-serif;max-width:860px;margin:2rem auto;padding:0 1rem}")
    append("article,fieldset{border:1px solid #ccc;border-radius:8px;padding:1rem;margin:1rem 0}blockquote{white-space:pre-wrap;background:#f4f4f4;padding:.5rem;margin:.5rem 0}")
    append("form{display:inline}button{margin:.25rem}label{display:block;margin:.35rem 0}</style></head><body>")
    append("<h1>Yönetim</h1>")

    append("<section><h2>Aktif faz</h2><form method=\"post\" action=\"${ApiYollari.YONETIM}/faz\"><fieldset>")
    append("<input type=\"hidden\" name=\"anahtar\" value=\"$anahtar\">")
    for (faz in Faz.entries) {
        val secili = if (faz == aktifFaz) " checked" else ""
        append("<label><input type=\"radio\" name=\"faz\" value=\"${faz.seviye}\"$secili> Faz ${faz.seviye}: ${FAZ_ACIKLAMASI.getValue(faz).html()}</label>")
    }
    append("<p>Fazlar birikimlidir; bir fazı kapatmak veriyi silmez, yalnızca gizler. Uygulama yeni fazı bir sonraki açılışta görür.</p>")
    append("<button>Kaydet</button></fieldset></form></section>")

    append("<section><h2>Açık şikayetler (${sikayetler.size})</h2>")
    if (sikayetler.isEmpty()) append("<p>Karar bekleyen şikayet yok.</p>")
    for (s in sikayetler) {
        append("<article><p><strong>${SEBEP_ADI.getValue(s.sebep).html()}</strong> · ${HEDEF_ADI.getValue(s.tur)}")
        append(" · @${s.hedef.orEmpty().html()} · ${s.zaman.toInstant()}</p>")
        append("<p>Şikayet eden: @${s.sikayetEden.orEmpty().html()}</p>")
        s.icerik?.let { append("<blockquote>${it.html()}</blockquote>") }
        s.aciklama?.let { append("<p>Açıklama: ${it.html()}</p>") }
        append("<form method=\"post\" action=\"${ApiYollari.YONETIM}/sikayetler/${s.id}\"><input type=\"hidden\" name=\"anahtar\" value=\"$anahtar\">")
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
    append("</section>")
    kuratorBasvurulari?.let { basvurular ->
        append("<section><h2>Küratör başvuruları (${basvurular.size})</h2>")
        if (basvurular.isEmpty()) append("<p>Karar bekleyen başvuru yok.</p>")
        for (b in basvurular) {
            append("<article><p><strong>@${b.kullaniciAdi.html()}</strong> · ${b.zaman.toInstant()}</p><p>")
            append(b.sartlar.joinToString(" · ") { "${it.mevcut}/${it.gereken} ${SART_ADI.getValue(it.tur)}" })
            append("</p><form method=\"post\" action=\"${ApiYollari.YONETIM}/kurator/${b.id}\"><input type=\"hidden\" name=\"anahtar\" value=\"$anahtar\">")
            append("<button name=\"karar\" value=\"ONAYLA\">Onayla</button><button name=\"karar\" value=\"REDDET\">Reddet</button></form></article>")
        }
        append("</section>")
    }
    append("</body></html>")
}
