package com.sosyaloyun.server

import com.sosyaloyun.shared.BildirimTuru
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.KullaniciListesi
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Günlük kaydı beğenileri; görme yetkisi çağıran tarafından önceden denetlenir. */
class Begeniler(private val veritabani: Database, private val saat: Clock) {
    suspend fun begen(kullaniciId: UUID, kayitId: UUID) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val simdi = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)
                val eklendi = BegeniTablosu.insertIgnore {
                    it[BegeniTablosu.kullaniciId] = kullaniciId
                    it[gunlukKaydiId] = kayitId
                    it[olusturulma] = simdi
                }.insertedCount
                if (eklendi == 1) with(BildirimOlaylari) { bildir(sahibi(kayitId), BildirimTuru.BEGENI, kullaniciId, simdi, kayitId) }
            }
        }
    }

    suspend fun geriAl(kullaniciId: UUID, kayitId: UUID) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                if (BegeniTablosu.deleteWhere { (BegeniTablosu.kullaniciId eq kullaniciId) and (gunlukKaydiId eq kayitId) } > 0) {
                    with(BildirimOlaylari) { bildirimiSil(sahibi(kayitId), BildirimTuru.BEGENI, kullaniciId, kayitId) }
                }
            }
        }
    }

    /** [izleyen]'le arasında engel olanlar listede görünmez. */
    suspend fun begenenler(kayitId: UUID, izleyen: UUID, imlec: String?): KullaniciListesi = withContext(Dispatchers.IO) {
        val baslangic = Imlec.baslangic(imlec)
        transaction(veritabani) {
            val kisiler = BegeniTablosu.join(KullaniciTablosu, JoinType.INNER, BegeniTablosu.kullaniciId, KullaniciTablosu.id)
                .selectAll()
                .where { (BegeniTablosu.gunlukKaydiId eq kayitId) and (BegeniTablosu.kullaniciId notInList engellileri(izleyen)) }
                .orderBy(BegeniTablosu.olusturulma to SortOrder.DESC)
                .limit(SAYFA + 1)
                .offset(baslangic.toLong())
                .mapNotNull { it.kullaniciOzeti() }
            KullaniciListesi(kisiler.take(SAYFA), Imlec.sonraki(kisiler.size, baslangic, SAYFA))
        }
    }

    /** Kayıtlara beğeni sayısını ve [izleyen]'in beğenip beğenmediğini ekler. */
    suspend fun ekle(izleyen: UUID, kayitlar: List<GunlukKaydi>): List<GunlukKaydi> {
        if (kayitlar.isEmpty()) return kayitlar
        val idler = kayitlar.map { UUID.fromString(it.id) }
        val (sayilar, begendiklerim) = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val sayi = Count(BegeniTablosu.kullaniciId)
                val sayilar = BegeniTablosu.select(BegeniTablosu.gunlukKaydiId, sayi)
                    .where { BegeniTablosu.gunlukKaydiId inList idler }
                    .groupBy(BegeniTablosu.gunlukKaydiId)
                    .associate { it[BegeniTablosu.gunlukKaydiId] to it[sayi].toInt() }
                val begendiklerim = BegeniTablosu.select(BegeniTablosu.gunlukKaydiId)
                    .where { (BegeniTablosu.gunlukKaydiId inList idler) and (BegeniTablosu.kullaniciId eq izleyen) }
                    .map { it[BegeniTablosu.gunlukKaydiId] }.toSet()
                sayilar to begendiklerim
            }
        }
        return kayitlar.map {
            val id = UUID.fromString(it.id)
            it.copy(begeniSayisi = sayilar[id] ?: 0, begendim = id in begendiklerim)
        }
    }

    suspend fun ekle(izleyen: UUID, kayit: GunlukKaydi): GunlukKaydi = ekle(izleyen, listOf(kayit)).single()

    private fun sahibi(kayitId: UUID): UUID =
        GunlukKaydiTablosu.select(GunlukKaydiTablosu.kullaniciId).where { GunlukKaydiTablosu.id eq kayitId }.single()[GunlukKaydiTablosu.kullaniciId]

    private companion object {
        const val SAYFA = 30
    }
}

internal object BegeniTablosu : Table("begeni") {
    val kullaniciId = javaUUID("kullanici_id")
    val gunlukKaydiId = javaUUID("gunluk_kaydi_id")
    val olusturulma = timestampWithTimeZone("olusturulma")
    override val primaryKey = PrimaryKey(gunlukKaydiId, kullaniciId)
}
