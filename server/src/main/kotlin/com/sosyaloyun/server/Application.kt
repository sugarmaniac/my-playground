package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiJson
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.SaglikYaniti
import com.sosyaloyun.shared.SunucuDurumu
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val bagimliliklar = Bagimliliklar.ortamdan()
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = { modul(bagimliliklar) })
        .start(wait = true)
}

fun Application.modul(bagimliliklar: Bagimliliklar) {
    val veritabani = veritabaniniHazirla(bagimliliklar.veriKaynagi)
    val ayarlar = Ayarlar(veritabani)
    val fazlar = Fazlar(ayarlar)
    val oturumlar = Oturumlar(veritabani)
    val moderasyon = Moderasyon(veritabani, ayarlar, bagimliliklar.epostaGonderici, bagimliliklar.saat)
    val kullanicilar = Kullanicilar(veritabani)
    val davetKodlari = DavetKodlari(veritabani, bagimliliklar.saat)
    runBlocking { davetKodlari.kurucuKodlariniEkle(bagimliliklar.kurucuDavetKodlari) }
    val katalog = Katalog(veritabani)
    val katalogIceAktarici = bagimliliklar.katalogKaynagi?.let { KatalogIceAktarici(it, katalog) }
    if (bagimliliklar.katalogZamanlayici && katalogIceAktarici != null) katalogZamanlayicisiniBaslat(katalogIceAktarici)

    oturumDogrulamasiKur(oturumlar, Aktiflik(veritabani, bagimliliklar.saat))
    if (bagimliliklar.metrikZamanlayici) metrikZamanlayicisiniBaslat(Metrikler(veritabani, bagimliliklar.epostaGonderici, bagimliliklar.saat))

    install(ContentNegotiation) {
        json(ApiJson)
    }
    routing {
        kimlikRotalari(bagimliliklar.kimlikDogrulayici, kullanicilar, oturumlar, moderasyon)
        sikayetRotalari(moderasyon)
        yonetimRotalari(moderasyon, fazlar, bagimliliklar.moderasyonSifresi)
        profilRotalari(kullanicilar, davetKodlari, ayarlar, fazlar)
        val gunluk = Gunluk(veritabani, bagimliliklar.saat)
        val kutuphane = Kutuphane(veritabani, bagimliliklar.saat)
        val begeniler = Begeniler(veritabani, bagimliliklar.saat)
        val sosyalGraf = SosyalGraf(veritabani, bagimliliklar.saat, fazlar)
        val steam = Steam(veritabani, katalog, kutuphane, bagimliliklar.steamKaynagi, bagimliliklar.saat)
        if (bagimliliklar.steamZamanlayici) steamZamanlayicisiniBaslat(steam)
        steamRotalari(steam, katalog, kutuphane, kullanicilar)
        bildirimRotalari(Bildirimler(veritabani, bagimliliklar.saat, fazlar))
        if (bagimliliklar.pushZamanlayici) pushZamanlayicisiniBaslat(PushDagitici(veritabani, ayarlar, bagimliliklar.pushGonderici, bagimliliklar.saat))
        kesifRotalari(KesifServisi(veritabani, katalog, bagimliliklar.saat), katalog)
        arkadasBulmaRotalari(ArkadasBulma(veritabani, bagimliliklar.steamKaynagi), kullanicilar, sosyalGraf)
        katalogRotalari(katalog, kutuphane, gunluk, begeniler, Topluluk(veritabani, ayarlar, fazlar), katalogIceAktarici, bagimliliklar.igdbWebhookSirri)
        akisRotalari(Akis(veritabani, katalog, begeniler), kutuphane, katalog)
        gunlukRotalari(gunluk, katalog, begeniler, sosyalGraf, kullanicilar, Yorumlar(veritabani, bagimliliklar.saat), fazlar)
        val profiller = Profiller(veritabani, katalog, kullanicilar, sosyalGraf, begeniler, bagimliliklar.saat)
        profilSayfasiRotalari(profiller, kullanicilar, sosyalGraf, fazlar)
        paylasimRotalari(Paylasim(veritabani, katalog, profiller, begeniler, bagimliliklar.saat, sosyalGraf), kullanicilar, bagimliliklar.webDizini)
        get(ApiYollari.SAGLIK) {
            val durum = if (ayarlar.oku(Ayar.BakimModu)) SunucuDurumu.BAKIMDA else SunucuDurumu.AYAKTA
            call.respond(
                SaglikYaniti(
                    durum = durum,
                    apiSurumu = SaglikYaniti.API_SURUMU,
                    sunucuZamani = bagimliliklar.saat.now(),
                ),
            )
        }
    }
}
