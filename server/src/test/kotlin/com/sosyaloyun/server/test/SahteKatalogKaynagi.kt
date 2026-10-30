package com.sosyaloyun.server.test

import com.sosyaloyun.server.KatalogKaynagi
import com.sosyaloyun.server.KaynakOyun
import com.sosyaloyun.server.KaynakPopulerlik

/** Testte doldurulan katalog; kullanıcı isteklerinin buraya hiç uğramadığı [cagriSayisi] ile doğrulanır. */
class SahteKatalogKaynagi : KatalogKaynagi {
    val oyunlar = mutableListOf<KaynakOyun>()
    val populerlikler = mutableListOf<KaynakPopulerlik>()
    var cagriSayisi = 0
        private set

    override suspend fun oyunSayfasi(sonKaynakId: Long, adet: Int): List<KaynakOyun> {
        cagriSayisi++
        return oyunlar.sortedBy { it.kaynakId }.filter { it.kaynakId > sonKaynakId }.take(adet)
    }

    override suspend fun oyunlar(kaynakIdleri: List<Long>): List<KaynakOyun> {
        cagriSayisi++
        return oyunlar.filter { it.kaynakId in kaynakIdleri }
    }

    override suspend fun enPopulerler(adet: Int): List<KaynakPopulerlik> {
        cagriSayisi++
        return populerlikler.sortedByDescending { it.deger }.take(adet)
    }

    override suspend fun populerlikSayfasi(sonKaynakId: Long, adet: Int): List<KaynakPopulerlik> {
        cagriSayisi++
        return populerlikler.sortedBy { it.oyunKaynakId }.filter { it.oyunKaynakId > sonKaynakId }.take(adet)
    }
}
