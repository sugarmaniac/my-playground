package com.sosyaloyun.server

import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.KESIF_PLATFORMLARI
import com.sosyaloyun.shared.Kesif
import com.sosyaloyun.shared.PopulerOyun
import com.sosyaloyun.shared.Tur
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Keşif ekranı: takip edilenler arasında bu hafta kaydedilenler, PopScore'a göre trend oyunlar, tür ve platform kısayolları. */
class Kesif(private val veritabani: Database, private val katalog: Katalog, private val saat: Clock) {
    suspend fun kesif(izleyen: UUID): Kesif {
        val populer = takipEdilenlerdePopuler(izleyen)
        val ozetler = katalog.ozetleri(populer.map { it.first })
        return Kesif(
            takipEdilenlerde = populer.mapNotNull { (oyunId, sayi) -> ozetler[oyunId]?.let { PopulerOyun(it, sayi) } },
            trend = katalog.populerler(TREND_SAYISI),
            turler = Tur.entries,
            platformlar = KESIF_PLATFORMLARI,
        )
    }

    /**
     * Son 7 günde takip edilenlerin girdiği, izleyenin görebileceği günlük kayıtları; oyun başına farklı kişi sayısına göre.
     * Sessize alınanlar sayılmaz; sessiz kayıtlar yalnızca akışa düşmez, burada sayılır.
     */
    private suspend fun takipEdilenlerdePopuler(izleyen: UUID): List<Pair<UUID, Int>> = withContext(Dispatchers.IO) {
        val baslangic = (saat.now() - 7.days).toJavaInstant().atOffset(ZoneOffset.UTC)
        transaction(veritabani) {
            val gizli = sessizeAldiklari(izleyen).toSet()
            val takipEdilenler = TakipTablosu.select(TakipTablosu.takipEdilen).where { TakipTablosu.takipEden eq izleyen }
                .map { it[TakipTablosu.takipEdilen] }.filterNot { it in gizli }
            if (takipEdilenler.isEmpty()) return@transaction emptyList()
            val arkadaslar = ArkadaslikTablosu.selectAll()
                .where { (ArkadaslikTablosu.kullaniciA eq izleyen) or (ArkadaslikTablosu.kullaniciB eq izleyen) }
                .map { if (it[ArkadaslikTablosu.kullaniciA] == izleyen) it[ArkadaslikTablosu.kullaniciB] else it[ArkadaslikTablosu.kullaniciA] }
            val g = GunlukKaydiTablosu
            // İzleyen hepsini takip ettiği için Herkes ve Takipçiler görünür; Arkadaşlar yalnızca arkadaşın kaydında.
            val gorunur = (g.gorunurluk inList listOf(Gorunurluk.HERKES.name, Gorunurluk.TAKIPCILER.name)) or
                ((g.gorunurluk eq Gorunurluk.ARKADASLAR.name) and (g.kullaniciId inList arkadaslar))
            g.select(g.oyunId, g.kullaniciId)
                .where { (g.kullaniciId inList takipEdilenler) and (g.olusturulma greaterEq baslangic) and gorunur }
                .withDistinct()
                .map { it[g.oyunId] }
                .groupingBy { it }.eachCount()
                .entries.sortedWith(compareByDescending<Map.Entry<UUID, Int>> { it.value }.thenBy { it.key.toString() })
                .take(POPULER_SAYISI)
                .map { it.key to it.value }
        }
    }

    private companion object {
        const val TREND_SAYISI = 20
        const val POPULER_SAYISI = 10
    }
}
