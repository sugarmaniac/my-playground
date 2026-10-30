package com.sosyaloyun.server

import io.ktor.server.application.Application
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("KatalogZamanlayicisi")

/**
 * Açılışta ve sonra günde bir: tam içe aktarma ve popülerlik güncellemesi. İçe aktarma tekrar
 * çalıştırılabilir; kaçırılan webhook'ları da telafi eder.
 */
fun Application.katalogZamanlayicisiniBaslat(iceAktarici: KatalogIceAktarici) {
    launch {
        while (true) {
            runCatching {
                log.info("Katalog içe aktarma başlıyor")
                iceAktarici.tamIceAktar()
                iceAktarici.populerlikleriGuncelle()
                log.info("Katalog içe aktarma bitti")
            }.onFailure { log.error("Katalog içe aktarma başarısız", it) }
            delay(24.hours)
        }
    }
}

/** Günde bir: bağlı Steam hesaplarının süreleri güncellenir (akışa düşmez). */
fun Application.steamZamanlayicisiniBaslat(steam: Steam) {
    launch {
        while (true) {
            delay(24.hours)
            runCatching { steam.hepsiniGuncelle() }.onFailure { log.error("Steam süreleri güncellenemedi", it) }
        }
    }
}

/** Bekleyen push'lar her 30 saniyede bir gönderilir; bildirim kendisi kaynak olayla birlikte yazılmıştır. */
fun Application.pushZamanlayicisiniBaslat(dagitici: PushDagitici) {
    launch {
        while (true) {
            runCatching { dagitici.bekleyenleriGonder() }.onFailure { log.error("Push'lar gönderilemedi", it) }
            delay(30.seconds)
        }
    }
}

/** Saatte bir bakılır; haftanın raporu henüz gitmediyse pazartesi sabahından itibaren ekibe gönderilir. */
fun Application.metrikZamanlayicisiniBaslat(metrikler: Metrikler) {
    launch {
        while (true) {
            runCatching { metrikler.haftalikRaporuGonder() }.onFailure { log.error("Haftalık metrikler gönderilemedi", it) }
            delay(1.hours)
        }
    }
}

/** Saatte bir bakılır; haftanın puan görüntüsü henüz alınmadıysa alınır (#51). Faz kapalıyken de çalışır. */
fun Application.puanGecmisiZamanlayicisiniBaslat(puanGecmisleri: PuanGecmisleri) {
    launch {
        while (true) {
            runCatching { puanGecmisleri.anlikGoruntuAl() }.onFailure { log.error("Puan görüntüsü alınamadı", it) }
            delay(1.hours)
        }
    }
}
