package com.sosyaloyun.server

import io.ktor.server.application.Application
import kotlin.time.Duration.Companion.hours
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
