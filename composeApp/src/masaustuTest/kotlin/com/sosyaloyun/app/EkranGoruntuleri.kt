package com.sosyaloyun.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.sosyaloyun.app.tema.SosyalOyunTemasi
import com.sosyaloyun.app.test.SahteApiIstemcisi
import com.sosyaloyun.shared.AkisOgesi
import com.sosyaloyun.shared.AkisOlayTuru
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.OyunTuru
import java.io.File
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.skia.EncodedImageFormat

/**
 * Tasarım tuvaliyle karşılaştırmak için ekranların PNG'leri (#59): `composeApp/build/ekran-goruntuleri/`.
 * Doğrulama yapmaz; görüntüler gözle incelenir.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EkranGoruntuleri {
    private val ayse = KullaniciOzeti("k1", "ayse", "Ayşe", Avatar.TILKI)
    private val mert = KullaniciOzeti("k2", "mert", "Mert", Avatar.KUMANDA)
    private val can = KullaniciOzeti("k3", "can", "Can", Avatar.EJDERHA)
    private val eldenRing = OyunOzeti("o1", "Elden Ring", OyunTuru.ANA_OYUN, 2022, null, listOf("PlayStation 5"), null)
    private val celeste = OyunOzeti("o2", "Celeste", OyunTuru.ANA_OYUN, 2018, null, listOf("PC (Microsoft Windows)"), null)

    private fun kayit(id: String, oyun: String, puan: Int? = null, sure: Int? = null, platform: String? = null, inceleme: String? = null, zaman: kotlin.time.Instant) =
        GunlukKaydi(
            id = id, oyunId = oyun, platform = platform, baslama = null, bitis = null, sureDakika = sure, puan = puan, inceleme = inceleme,
            spoiler = false, gorunurluk = Gorunurluk.HERKES, sessiz = false, olusturulma = zaman,
        )

    private fun sahteApi() = SahteApiIstemcisi().apply {
        davetKodlari = listOf(com.sosyaloyun.shared.DavetKodu("K7Q2MX", null, false), com.sosyaloyun.shared.DavetKodu("P4ZR8T", ayse, true))
        profilSayfasi = profilSayfasi.copy(kullanici = ayse)
        akisYeni = 3
        val simdi = Clock.System.now()
        akisOgeleri = listOf(
            AkisOgesi(
                "a1", AkisOlayTuru.GUNLUK_KAYDI, mert, simdi - 2.hours, eldenRing,
                kayit("g1", "o1", puan = 9, sure = 86 * 60, platform = "PS5", zaman = simdi - 2.hours).copy(begeniSayisi = 24),
            ),
            AkisOgesi(
                "a2", AkisOlayTuru.GUNLUK_KAYDI, can, simdi - 5.hours, celeste,
                kayit("g2", "o2", puan = 10, inceleme = "Zor ama asla haksız değil. Her ölüm bir sonraki denemeye küçük bir ders bırakıyor; B-tarafı bölümleri ise başlı başına bir oyun kadar dolu.", zaman = simdi - 5.hours),
                istekListemde = true,
            ),
            AkisOgesi("a3", AkisOlayTuru.ARKADAS_OLDU, ayse, simdi - 26.hours, arkadas = mert),
        )
        val hades = OyunOzeti("o3", "Hades", OyunTuru.ANA_OYUN, 2020, null, listOf("PC", "PS5", "Switch"), null)
        val hollow = OyunOzeti("o4", "Hollow Knight", OyunTuru.ANA_OYUN, 2017, null, emptyList(), null)
        val bg3 = OyunOzeti("o5", "Baldur's Gate 3", OyunTuru.ANA_OYUN, 2023, null, emptyList(), null)
        kesifYaniti = com.sosyaloyun.shared.Kesif(
            takipEdilenlerde = listOf(com.sosyaloyun.shared.PopulerOyun(hades, 5), com.sosyaloyun.shared.PopulerOyun(hollow, 4), com.sosyaloyun.shared.PopulerOyun(bg3, 3)),
            trend = listOf(eldenRing, celeste, hades),
            turler = com.sosyaloyun.shared.Tur.entries.take(6),
            platformlar = listOf("PC (Microsoft Windows)", "PlayStation 5", "Nintendo Switch"),
        )
        oyunDetaylari = mapOf(
            "o1" to com.sosyaloyun.shared.OyunDetayi(
                oyun = eldenRing, aciklama = "Ara Diyarlar'da geçen, FromSoftware imzalı açık dünya aksiyon RPG.",
                gelistiriciler = listOf("FromSoftware"), yayincilar = listOf("Bandai Namco"), turler = listOf("Role-playing (RPG)", "Adventure"),
                magazaLinkleri = emptyList(), eklentiler = emptyList(), kaynakUrl = "https://www.igdb.com",
                kutuphaneGirisi = com.sosyaloyun.shared.KutuphaneGirisi(durum = com.sosyaloyun.shared.Durum.BITIRDIM, puan = 9),
                puanlar = com.sosyaloyun.shared.OyunPuanlari(toplulukPuani = 8.6, arkadasOrtalamasi = 8.4),
            ),
            // Telefondan gelen ekran görüntüsündeki gibi uzun künye, puansız, Oynuyorum seçili (#61).
            "o6" to com.sosyaloyun.shared.OyunDetayi(
                oyun = OyunOzeti("o6", "War Thunder", OyunTuru.ANA_OYUN, 2013, null, listOf("PC (Microsoft Windows)", "Xbox Series X|S", "PlayStation 4", "Linux", "Mac"), null),
                aciklama = "Araç savaşı MMO'su.", gelistiriciler = listOf("Gaijin Entertainment"), yayincilar = emptyList(),
                turler = listOf("Shooter", "Simulator"), magazaLinkleri = emptyList(), eklentiler = emptyList(), kaynakUrl = "https://www.igdb.com",
                kutuphaneGirisi = com.sosyaloyun.shared.KutuphaneGirisi(durum = com.sosyaloyun.shared.Durum.OYNUYORUM, puan = 7, begendim = true),
            ),
        )
        profilSayfasi = profilSayfasi.copy(biyografi = "Roguelike bağımlısı, metroidvania avcısı. Backlog'um benden uzun.", buYilBitirilen = 23, vitrin = listOf(hades, hollow, bg3, eldenRing), takipciSayisi = 1240, takipEdilenSayisi = 186)
    }

    @Test
    fun ekranlar() {
        val oncekiYerel = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val api = sahteApi()
            ciz("1-akis") { UygulamaGezintisi(api, ayse, cikisYap = {}, hesapSilindi = {}) }
            ciz("2-kesif") {
                com.sosyaloyun.app.kesif.KesifEkrani(com.sosyaloyun.app.kesif.KesifViewModel(api), oyunaGit = {}, turAc = {}, platformAc = {})
            }
            ciz("3-oyun-detay") {
                com.sosyaloyun.app.oyun.OyunDetayEkrani(
                    com.sosyaloyun.app.oyun.OyunDetayViewModel(api, "o1"), api, geri = {}, oyunaGit = {}, gunlugeYaz = {}, kullaniciyaGit = {}, gunlukKaydinaGit = {},
                )
            }
            ciz("4-profil") {
                com.sosyaloyun.app.profil.ProfilSayfasiEkrani(
                    com.sosyaloyun.app.profil.ProfilSayfasiViewModel(api, webAdresi = "https://ornek.app"),
                    profiliDuzenle = {}, vitriniDuzenle = {}, oyunaGit = {}, kisiListesi = { _, _ -> }, gunlukKaydinaGit = {},
                )
            }
            ciz("5-ayarlar", yukseklik = 1900) {
                com.sosyaloyun.app.hesap.AnaEkran(
                    profil = ayse,
                    davetKodlari = com.sosyaloyun.app.hesap.DavetKodlarimViewModel(api),
                    geri = {},
                    steamAyari = com.sosyaloyun.app.onboarding.SteamAyariViewModel(api),
                    bildirimTercihleri = com.sosyaloyun.app.bildirim.BildirimTercihleriViewModel(api),
                    engelListesi = {},
                    cikisYap = {},
                    hesapSilme = { error("açılmaz") },
                    gizlilik = { com.sosyaloyun.app.gizlilik.GizlilikViewModel(api) },
                    kurator = { com.sosyaloyun.app.kurator.KuratorViewModel(api) },
                    itiraz = { com.sosyaloyun.app.itiraz.ItirazViewModel(api) },
                    konsol = { com.sosyaloyun.app.konsol.KonsolViewModel(api) },
                )
            }
            // Büyük yazı boyutu ayarlı telefonlar (Honor varsayılanı gibi) ve uzun adlar: taşma denetimi (#61).
            val uzunAdli = sahteApi().apply { profilSayfasi = profilSayfasi.copy(kullanici = profilSayfasi.kullanici.copy(gorunenAd = "Sugarmaniac")) }
            ciz("6-oyun-detay-buyuk-yazi", yaziOlcegi = 1.3f) {
                com.sosyaloyun.app.oyun.OyunDetayEkrani(
                    com.sosyaloyun.app.oyun.OyunDetayViewModel(api, "o6"), api, geri = {}, oyunaGit = {}, gunlugeYaz = {}, kullaniciyaGit = {}, gunlukKaydinaGit = {},
                )
            }
            ciz("7-profil-buyuk-yazi", yaziOlcegi = 1.3f) {
                com.sosyaloyun.app.profil.ProfilSayfasiEkrani(
                    com.sosyaloyun.app.profil.ProfilSayfasiViewModel(uzunAdli, webAdresi = "https://ornek.app"),
                    profiliDuzenle = {}, vitriniDuzenle = {}, oyunaGit = {}, kisiListesi = { _, _ -> }, gunlukKaydinaGit = {},
                )
            }
            ciz("8-akis-buyuk-yazi", yaziOlcegi = 1.3f) { UygulamaGezintisi(api, ayse, cikisYap = {}, hesapSilindi = {}) }
            ciz("9-kesif-buyuk-yazi", yaziOlcegi = 1.3f) {
                com.sosyaloyun.app.kesif.KesifEkrani(com.sosyaloyun.app.kesif.KesifViewModel(api), oyunaGit = {}, turAc = {}, platformAc = {})
            }
        } finally {
            Dispatchers.resetMain()
            java.util.Locale.setDefault(oncekiYerel)
        }
    }

    private fun ciz(ad: String, yukseklik: Int = 844, yaziOlcegi: Float = 1f, icerik: @Composable () -> Unit) {
        val sahip = object : ViewModelStoreOwner, LifecycleOwner {
            override val viewModelStore = ViewModelStore()
            override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        }
        ImageComposeScene(width = 390 * 3, height = yukseklik * 3, density = Density(3f, yaziOlcegi)) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides sahip, LocalLifecycleOwner provides sahip, LocalAktifFaz provides Faz.FAZ_3) {
                SosyalOyunTemasi { androidx.compose.material3.Surface(color = com.sosyaloyun.app.tema.Renk.Zemin, content = icerik) }
            }
        }.use { sahne ->
            repeat(5) { sahne.render(it * 100_000_000L) }
            val png = sahne.render(1_000_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File("build/ekran-goruntuleri").apply { mkdirs() }.resolve("$ad.png").writeBytes(png)
        }
    }
}
