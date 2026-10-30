package com.sosyaloyun.server.test

import com.sosyaloyun.server.KonsolHesabi
import com.sosyaloyun.server.KonsolKaynagi
import com.sosyaloyun.server.KonsolOyunlari
import com.sosyaloyun.shared.KonsolAgi

/** Testlerde Xbox/PlayStation: hesap adı → oyun geçmişi; [hata] doluysa servise ulaşılamıyormuş gibi davranır. */
class SahteKonsolKaynagi(override val ag: KonsolAgi) : KonsolKaynagi {
    val hesaplar = mutableMapOf<String, KonsolOyunlari>()
    var hata: Exception? = null

    override suspend fun hesapBul(ad: String): KonsolHesabi? {
        hata?.let { throw it }
        return ad.takeIf { it in hesaplar }?.let { KonsolHesabi("kimlik-$it", it) }
    }

    override suspend fun oyunlar(kimlik: String): KonsolOyunlari {
        hata?.let { throw it }
        return hesaplar.getValue(kimlik.removePrefix("kimlik-"))
    }
}
