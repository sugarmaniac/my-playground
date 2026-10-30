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
    val oturumlar = Oturumlar(veritabani)
    val kullanicilar = Kullanicilar(veritabani)
    val davetKodlari = DavetKodlari(veritabani, bagimliliklar.saat)
    runBlocking { davetKodlari.kurucuKodlariniEkle(bagimliliklar.kurucuDavetKodlari) }
    val katalog = Katalog(veritabani)
    val katalogIceAktarici = bagimliliklar.katalogKaynagi?.let { KatalogIceAktarici(it, katalog) }
    if (bagimliliklar.katalogZamanlayici && katalogIceAktarici != null) katalogZamanlayicisiniBaslat(katalogIceAktarici)

    oturumDogrulamasiKur(oturumlar)

    install(ContentNegotiation) {
        json(ApiJson)
    }
    routing {
        kimlikRotalari(bagimliliklar.kimlikDogrulayici, kullanicilar, oturumlar)
        profilRotalari(kullanicilar, davetKodlari, ayarlar)
        val gunluk = Gunluk(veritabani, bagimliliklar.saat)
        val kutuphane = Kutuphane(veritabani, bagimliliklar.saat)
        katalogRotalari(katalog, kutuphane, gunluk, katalogIceAktarici, bagimliliklar.igdbWebhookSirri)
        akisRotalari(Akis(veritabani, katalog), kutuphane, katalog)
        gunlukRotalari(gunluk, katalog)
        val sosyalGraf = SosyalGraf(veritabani, bagimliliklar.saat)
        profilSayfasiRotalari(Profiller(veritabani, katalog, kullanicilar, sosyalGraf, bagimliliklar.saat), kullanicilar, sosyalGraf)
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
