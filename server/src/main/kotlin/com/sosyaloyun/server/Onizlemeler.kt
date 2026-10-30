package com.sosyaloyun.server

import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.ListeGorunurlugu
import java.io.File
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Paylaşım sayfasının önizleme etiketleri; [gorsel] mutlak adres (oyun kapağı). */
data class Onizleme(val baslik: String, val aciklama: String, val gorsel: String?)

/** Web sitesinin paylaşım sayfaları. */
enum class PaylasimSayfasi(val dosya: String, val parametre: String) {
    KAYIT("kayit.html", "id"),
    PROFIL("profil.html", "ad"),
    LISTE("liste.html", "id"),
}

/**
 * Paylaşım önizlemeleri ve arama motorları (Faz 2, #54). Faz 2'de paylaşım sayfaları içeriğe özel başlık, açıklama ve
 * Open Graph/Twitter etiketleriyle sunulur ve sitemap'e girer; içerik yine tarayıcıda yüklenir. Yalnızca paylaşım uç
 * noktalarının döndürdüğü içerik (Herkes görünürlüğünde, gizli olmayan, yaptırımsız) etiketlenir. Faz 1'de ve
 * paylaşılamayan içerikte sayfa statik dosyadaki gibi genel etiketli ve `noindex` kalır.
 */
class Onizlemeler(
    private val veritabani: Database,
    private val paylasim: Paylasim,
    private val listeler: Listeler,
    private val kullanicilar: Kullanicilar,
    private val fazlar: Fazlar,
    private val saat: Clock,
) {
    suspend fun acik(): Boolean = fazlar.acik(Faz.FAZ_2)

    suspend fun onizleme(sayfa: PaylasimSayfasi, deger: String?): Onizleme? {
        deger ?: return null
        return when (sayfa) {
            PaylasimSayfasi.KAYIT -> uuid(deger)?.let { paylasim.kayit(it) }?.let { d ->
                // İşaretli spoiler önizlemeye taşınmaz.
                val metin = d.kayit.inceleme?.takeUnless { d.kayit.spoiler }
                Onizleme(
                    "${d.oyun.ad} · ${d.sahip.gorunenAd}",
                    metin?.let(::kisalt) ?: "${d.sahip.gorunenAd}, ${d.oyun.ad} oyununu günlüğüne yazdı.",
                    d.oyun.kapakUrl,
                )
            }
            PaylasimSayfasi.PROFIL -> kullanicilar.kullaniciAdindan(deger)?.let { paylasim.profil(it) }?.let { p ->
                val k = p.profil.kullanici
                Onizleme(
                    "${k.gorunenAd} (@${k.kullaniciAdi})",
                    p.profil.biyografi?.let(::kisalt) ?: "${k.gorunenAd} oyun günlüğünü Sosyal Oyun'da tutuyor.",
                    p.profil.vitrin.firstNotNullOfOrNull { it.kapakUrl },
                )
            }
            PaylasimSayfasi.LISTE -> uuid(deger)?.let { listeler.getir(it, izleyen = null) }?.let { l ->
                Onizleme(
                    l.baslik,
                    l.aciklama?.let(::kisalt) ?: "${l.sahip.gorunenAd} tarafından ${l.oyunlar.size} oyunluk liste.",
                    l.oyunlar.firstNotNullOfOrNull { it.kapakUrl },
                )
            }
        }
    }

    /**
     * Statik sayfaya önizleme etiketlerini yazar: `noindex` kalkar, `<title>` içeriğe özel başlık ve etiketlerle
     * değişir. Bütün kullanıcı metni HTML'e kaçışlanır.
     */
    fun etiketle(html: String, onizleme: Onizleme, adres: String): String {
        val baslik = "${onizleme.baslik} · Sosyal Oyun".html()
        val aciklama = onizleme.aciklama.html()
        val etiketler = buildString {
            append("<title>$baslik</title>\n")
            append("  <meta name=\"description\" content=\"$aciklama\">\n")
            append("  <meta property=\"og:site_name\" content=\"Sosyal Oyun\">\n")
            append("  <meta property=\"og:type\" content=\"article\">\n")
            append("  <meta property=\"og:title\" content=\"$baslik\">\n")
            append("  <meta property=\"og:description\" content=\"$aciklama\">\n")
            append("  <meta property=\"og:url\" content=\"${adres.html()}\">\n")
            onizleme.gorsel?.let { append("  <meta property=\"og:image\" content=\"${it.html()}\">\n") }
            append("  <meta name=\"twitter:card\" content=\"${if (onizleme.gorsel != null) "summary_large_image" else "summary"}\">\n")
            append("  <meta name=\"twitter:title\" content=\"$baslik\">\n")
            append("  <meta name=\"twitter:description\" content=\"$aciklama\">\n")
            onizleme.gorsel?.let { append("  <meta name=\"twitter:image\" content=\"${it.html()}\">\n") }
            append("  <link rel=\"canonical\" href=\"${adres.html()}\">")
        }
        return html.replace(NOINDEX, "").replaceFirst(BASLIK, Regex.escapeReplacement(etiketler))
    }

    /** Sitemap'teki adresler (web sitesine göre, `kayit.html?id=…`), en yeni önce, tür başına en fazla [SITEMAP_SINIRI]. */
    suspend fun sitemapYollari(): List<String> {
        val simdi = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)
        return withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val k = KullaniciTablosu
                // Faz 2 açıkken çağrılır; gizli profiller ve yaptırımlı hesaplar hiçbir türde yer almaz.
                fun acikMi(satir: ResultRow) = satir.paylasilabilir(simdi) && !satir[k.gizli]
                val profiller = k.selectAll().where { k.kullaniciAdi.isNotNull() }
                    .orderBy(k.davetKabulZamani to SortOrder.DESC_NULLS_LAST).limit(SITEMAP_SINIRI)
                    .filter(::acikMi).map { "profil.html?ad=${it[k.kullaniciAdi]}" }
                val g = GunlukKaydiTablosu
                val kayitlar = g.join(k, JoinType.INNER, g.kullaniciId, k.id).selectAll()
                    .where { g.gorunurluk eq Gorunurluk.HERKES.name }
                    .orderBy(g.olusturulma to SortOrder.DESC).limit(SITEMAP_SINIRI)
                    .filter(::acikMi).map { "kayit.html?id=${it[g.id]}" }
                val l = ListeTablosu
                val listeler = l.join(k, JoinType.INNER, l.kullaniciId, k.id).selectAll()
                    .where { l.gorunurluk eq ListeGorunurlugu.HERKES.name }
                    .orderBy(l.guncellenme to SortOrder.DESC).limit(SITEMAP_SINIRI)
                    .filter(::acikMi).map { "liste.html?id=${it[l.id]}" }
                profiller + kayitlar + listeler
            }
        }
    }

    private fun uuid(deger: String) = runCatching { UUID.fromString(deger) }.getOrNull()

    private fun kisalt(metin: String): String {
        val tek = metin.replace(Regex("\\s+"), " ").trim()
        return if (tek.length <= ACIKLAMA_SINIRI) tek else tek.take(ACIKLAMA_SINIRI - 1).trimEnd() + "…"
    }

    companion object {
        const val SITEMAP_SINIRI = 10_000
        private const val ACIKLAMA_SINIRI = 200
        private val NOINDEX = Regex("""\s*<meta name="robots" content="noindex">""")
        private val BASLIK = Regex("<title>.*?</title>")

        /** Statik sayfa dosyasını okur; yoksa null. */
        fun sayfaDosyasi(webDizini: File, sayfa: PaylasimSayfasi): String? =
            File(webDizini, sayfa.dosya).takeIf { it.isFile }?.readText()
    }
}
