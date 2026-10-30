package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.KuratorSartTuru
import com.sosyaloyun.shared.SikayetSebebi
import com.sosyaloyun.shared.YaptirimTuru
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
 * Ekibin yönetim paneli (#46): aktif faz ve moderasyon. HTTP Basic (şifre MODERASYON_SIFRESI; kullanıcı adı herhangi,
 * denetim kaydına "yapan" olarak yazılır); şifre tanımlı değilse panel yok. Formlar şifreden türetilen bir anahtar taşır.
 * Her karar denetim kaydına yazılır (#53).
 */
fun Route.yonetimRotalari(moderasyon: Moderasyon, fazlar: Fazlar, kuratorler: Kuratorler, itirazlar: Itirazlar, denetim: Denetim, sifre: String?) {
    get(ApiYollari.MODERASYON) { call.respondRedirect(ApiYollari.YONETIM) }

    get(ApiYollari.YONETIM) {
        val yonetici = call.yoneticiMi(sifre) ?: return@get
        val aktif = fazlar.aktif()
        // Faz 2 bölümleri yalnızca faz açıkken gösterilir; veri (denetim kaydı dahil) yine tutulur.
        val faz2 = if (aktif.kapsar(Faz.FAZ_2)) Faz2Bolumleri(kuratorler.bekleyenler(), itirazlar.bekleyenler(), denetim.son()) else null
        call.respondText(yonetimSayfasi(aktif, moderasyon.acikSikayetler(), faz2, formAnahtari(yonetici.sifre)), ContentType.Text.Html)
    }
    post("${ApiYollari.YONETIM}/kurator/{id}") {
        val (yonetici, form) = call.yonetimFormu(sifre) ?: return@post
        val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val onay = when (form["karar"]) {
            "ONAYLA" -> true
            "REDDET" -> false
            else -> null
        }
        if (id == null || onay == null) return@post call.respond(HttpStatusCode.BadRequest)
        kuratorler.karar(id, onay)?.let { ad -> denetim.yaz(yonetici.ad, if (onay) "Küratör onayı" else "Küratör reddi", "@$ad") }
        call.respondRedirect(ApiYollari.YONETIM)
    }
    post("${ApiYollari.YONETIM}/faz") {
        val (yonetici, form) = call.yonetimFormu(sifre) ?: return@post
        val faz = form["faz"]?.toIntOrNull()?.let { s -> Faz.entries.firstOrNull { it.seviye == s } }
            ?: return@post call.respond(HttpStatusCode.BadRequest)
        val onceki = fazlar.aktif()
        fazlar.degistir(faz)
        if (onceki != faz) denetim.yaz(yonetici.ad, "Faz değişikliği", "Faz ${onceki.seviye} → Faz ${faz.seviye}")
        call.respondRedirect(ApiYollari.YONETIM)
    }
    post("${ApiYollari.YONETIM}/sikayetler/{id}") {
        val (yonetici, form) = call.yonetimFormu(sifre) ?: return@post
        val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val karar = form["karar"]?.let { k -> SikayetDurumu.entries.firstOrNull { it.name == k && it != SikayetDurumu.ACIK } }
            ?.takeIf { it !in FAZ2_KARARLARI || fazlar.acik(Faz.FAZ_2) }
        if (id == null || karar == null) return@post call.respond(HttpStatusCode.BadRequest)
        moderasyon.karar(id, karar)?.let { hedef -> denetim.yaz(yonetici.ad, "Şikayet: ${KARAR_ADI.getValue(karar)}", "@$hedef") }
        call.respondRedirect(ApiYollari.YONETIM)
    }
    post("${ApiYollari.YONETIM}/itirazlar/{id}") {
        val (yonetici, form) = call.yonetimFormu(sifre) ?: return@post
        if (!fazlar.acik(Faz.FAZ_2)) return@post call.respond(HttpStatusCode.NotFound)
        val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val kabul = when (form["karar"]) {
            "KABUL" -> true
            "RED" -> false
            else -> null
        }
        if (id == null || kabul == null) return@post call.respond(HttpStatusCode.BadRequest)
        itirazlar.karar(id, kabul)?.let { ad -> denetim.yaz(yonetici.ad, if (kabul) "İtiraz kabulü, yaptırım kalktı" else "İtiraz reddi", "@$ad") }
        call.respondRedirect(ApiYollari.YONETIM)
    }
}

private val FAZ2_KARARLARI = setOf(SikayetDurumu.UYARILDI, SikayetDurumu.YORUM_YASAKLANDI)

private val KARAR_ADI = mapOf(
    SikayetDurumu.ACIK to "Açık",
    SikayetDurumu.REDDEDILDI to "Reddet",
    SikayetDurumu.ICERIK_KALDIRILDI to "İçeriği kaldır",
    SikayetDurumu.ASKIYA_ALINDI to "Hesabı askıya al",
    SikayetDurumu.BANLANDI to "Kalıcı ban",
    SikayetDurumu.UYARILDI to "Uyar",
    SikayetDurumu.YORUM_YASAKLANDI to "Yorum yasağı",
)

/** Panele giren ekip üyesi; [ad] HTTP Basic kullanıcı adı, boşsa "yönetici". */
internal class Yonetici(val ad: String, val sifre: String)

private class Faz2Bolumleri(val kuratorBasvurulari: List<BekleyenKuratorBasvurusu>, val itirazlar: List<BekleyenItiraz>, val denetim: List<DenetimKaydi>)

/** Şifre ve form anahtarı doğruysa yöneticiyi ve formu döner; değilse yanıtı verip null döner. */
internal suspend fun RoutingCall.yonetimFormu(sifre: String?): Pair<Yonetici, Parameters>? {
    val yonetici = yoneticiMi(sifre) ?: return null
    val form = receiveParameters()
    // Başka bir siteden gönderilen form şifreyi bilmeden bu anahtarı üretemez.
    if (!MessageDigest.isEqual(form["anahtar"].orEmpty().toByteArray(), formAnahtari(yonetici.sifre).toByteArray())) {
        respond(HttpStatusCode.Forbidden)
        return null
    }
    return yonetici to form
}

/** Şifre doğruysa yöneticiyi döner; değilse 401 (ya da panel kapalıysa 404) yanıtını verip null döner. */
private suspend fun RoutingCall.yoneticiMi(sifre: String?): Yonetici? {
    if (sifre.isNullOrEmpty()) {
        respond(HttpStatusCode.NotFound)
        return null
    }
    val kimlik = request.headers[HttpHeaders.Authorization]
        ?.takeIf { it.startsWith("Basic ") }
        ?.let { runCatching { String(Base64.getDecoder().decode(it.removePrefix("Basic ").trim())) }.getOrNull() }
    val gelen = kimlik?.substringAfter(':', "")
    if (gelen == null || !MessageDigest.isEqual(gelen.toByteArray(), sifre.toByteArray())) {
        response.header(HttpHeaders.WWWAuthenticate, "Basic realm=\"yonetim\", charset=\"UTF-8\"")
        respond(HttpStatusCode.Unauthorized)
        return null
    }
    return Yonetici(kimlik.substringBefore(':').trim().ifEmpty { "yönetici" }.take(64), sifre)
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

private fun yonetimSayfasi(aktifFaz: Faz, sikayetler: List<AcikSikayet>, faz2: Faz2Bolumleri?, anahtar: String): String = buildString {
    append("<!doctype html><html lang=\"tr\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
    append("<title>Yönetim</title><style>body{font-family:system-ui,sans-serif;max-width:860px;margin:2rem auto;padding:0 1rem}")
    append("article,fieldset{border:1px solid #ccc;border-radius:8px;padding:1rem;margin:1rem 0}blockquote{white-space:pre-wrap;background:#f4f4f4;padding:.5rem;margin:.5rem 0}")
    append("form{display:inline}button{margin:.25rem}label{display:block;margin:.35rem 0}table{border-collapse:collapse;width:100%}td,th{border-bottom:1px solid #ddd;padding:.3rem;text-align:left;font-size:.9rem}</style></head><body>")
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
        val kararlar = SikayetDurumu.entries.filter { it != SikayetDurumu.ACIK && (faz2 != null || it !in FAZ2_KARARLARI) }
        for (karar in kararlar) {
            append("<button name=\"karar\" value=\"${karar.name}\">${KARAR_ADI.getValue(karar)}</button>")
        }
        append("</form></article>")
    }
    append("</section>")
    faz2?.kuratorBasvurulari?.let { basvurular ->
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
    faz2?.itirazlar?.let { liste ->
        append("<section><h2>İtirazlar (${liste.size})</h2>")
        if (liste.isEmpty()) append("<p>Karar bekleyen itiraz yok.</p>")
        for (i in liste) {
            append("<article><p><strong>@${i.kullaniciAdi.html()}</strong> · ${YAPTIRIM_ADI.getValue(i.yaptirim)} (${i.yaptirimZamani.toInstant()}) · itiraz ${i.zaman.toInstant()}</p>")
            append("<blockquote>${i.metin.html()}</blockquote>")
            append("<form method=\"post\" action=\"${ApiYollari.YONETIM}/itirazlar/${i.id}\"><input type=\"hidden\" name=\"anahtar\" value=\"$anahtar\">")
            append("<button name=\"karar\" value=\"KABUL\">Yaptırımı kaldır</button><button name=\"karar\" value=\"RED\">İtirazı reddet</button></form></article>")
        }
        append("</section>")
    }
    faz2?.denetim?.let { kayitlar ->
        append("<section><h2>Denetim kaydı</h2>")
        if (kayitlar.isEmpty()) append("<p>Henüz karar yok.</p>")
        else {
            append("<table><thead><tr><th>Zaman</th><th>Kim</th><th>Eylem</th><th>Ayrıntı</th></tr></thead><tbody>")
            for (k in kayitlar) append("<tr><td>${k.zaman.toInstant()}</td><td>${k.yapan.html()}</td><td>${k.eylem.html()}</td><td>${k.ayrinti.html()}</td></tr>")
            append("</tbody></table>")
        }
        append("</section>")
    }
    append("</body></html>")
}

private val YAPTIRIM_ADI = mapOf(
    YaptirimTuru.ICERIK_KALDIRMA to "İçerik kaldırma",
    YaptirimTuru.ASKI to "Askı",
    YaptirimTuru.BAN to "Ban",
    YaptirimTuru.UYARI to "Uyarı",
    YaptirimTuru.YORUM_YASAGI to "Yorum yasağı",
)
