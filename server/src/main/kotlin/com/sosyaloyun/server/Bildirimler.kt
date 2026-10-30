package com.sosyaloyun.server

import com.sosyaloyun.shared.Bildirim
import com.sosyaloyun.shared.BildirimListesi
import com.sosyaloyun.shared.BildirimTercihleri
import com.sosyaloyun.shared.BildirimTuru
import com.sosyaloyun.shared.CihazKaydiIstegi
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

/** Bildirimler kaynak olayı yazan işlemin içinde yazılır ve geri alınınca (beğeni, takip, istek) silinir. */
internal object BildirimOlaylari {
    fun JdbcTransaction.bildir(
        alici: UUID,
        tur: BildirimTuru,
        aktor: UUID,
        zaman: OffsetDateTime,
        gunlukKaydiId: UUID? = null,
        yorumId: UUID? = null,
    ) {
        if (alici == aktor) return
        BildirimTablosu.insert {
            it[id] = UUID.randomUUID()
            it[BildirimTablosu.alici] = alici
            it[BildirimTablosu.tur] = tur.name
            it[BildirimTablosu.aktor] = aktor
            it[BildirimTablosu.gunlukKaydiId] = gunlukKaydiId
            it[BildirimTablosu.yorumId] = yorumId
            it[olusturulma] = zaman
        }
    }

    /** Moderasyon kalıp mesajı: bildirimi doğuran bir kişi yok. */
    fun JdbcTransaction.kalipMesaj(alici: UUID, tur: BildirimTuru, zaman: OffsetDateTime) {
        BildirimTablosu.insert {
            it[id] = UUID.randomUUID()
            it[BildirimTablosu.alici] = alici
            it[BildirimTablosu.tur] = tur.name
            it[olusturulma] = zaman
        }
    }

    fun JdbcTransaction.bildirimiSil(alici: UUID, tur: BildirimTuru, aktor: UUID, gunlukKaydiId: UUID? = null) {
        BildirimTablosu.deleteWhere {
            var kosul = (BildirimTablosu.alici eq alici) and (BildirimTablosu.tur eq tur.name) and (BildirimTablosu.aktor eq aktor)
            if (gunlukKaydiId != null) kosul = kosul and (BildirimTablosu.gunlukKaydiId eq gunlukKaydiId)
            kosul
        }
    }

    /** Engellenince iki taraf arasındaki bildirimler silinir. */
    fun JdbcTransaction.aradakiBildirimleriSil(a: UUID, b: UUID) {
        BildirimTablosu.deleteWhere { ((alici eq a) and (aktor eq b)) or ((alici eq b) and (aktor eq a)) }
    }
}

class Bildirimler(private val veritabani: Database, private val saat: Clock, private val fazlar: Fazlar) {
    /** Son [SON_BILDIRIM] bildirim, yeniden eskiye; beğeniler günlük kaydı başına gruplanır. Engelliler ve henüz profili olmayanlar görünmez. */
    suspend fun liste(alici: UUID): BildirimListesi {
        val kapali = kapaliTurler()
        return withContext(Dispatchers.IO) { transaction(veritabani) {
            val b = BildirimTablosu
            val satirlar = b.join(KullaniciTablosu, JoinType.LEFT, b.aktor, KullaniciTablosu.id)
                .selectAll()
                .where {
                    (b.alici eq alici) and (b.aktor.isNull() or (b.aktor notInList engellileri(alici))) and (b.tur notInList kapali)
                }
                .orderBy(b.olusturulma to SortOrder.DESC, b.id to SortOrder.DESC)
                .limit(SON_BILDIRIM)
                // Kişisi olan ama henüz ad seçmemiş aktörün bildirimi, adını seçince görünür.
                .mapNotNull { satir -> if (satir[b.aktor] == null) satir to null else satir.kullaniciOzeti()?.let { satir to it } }
            val gruplar = satirlar.groupBy { (satir, _) ->
                if (satir[b.tur] == BildirimTuru.BEGENI.name) "begeni:${satir[b.gunlukKaydiId]}" else "tek:${satir[b.id]}"
            }
            val bildirimler = gruplar.values.map { grup ->
                val (enYeni, kisi) = grup.first()
                Bildirim(
                    id = enYeni[b.id].toString(),
                    tur = BildirimTuru.valueOf(enYeni[b.tur]),
                    kisi = kisi,
                    zaman = enYeni[b.olusturulma].toInstant().let { Instant.fromEpochSeconds(it.epochSecond, it.nano) },
                    okundu = grup.all { (satir, _) -> satir[b.okundu] },
                    digerSayisi = grup.map { (satir, _) -> satir[b.aktor] }.distinct().size - 1,
                    gunlukKaydiId = enYeni[b.gunlukKaydiId]?.toString(),
                )
            }
            BildirimListesi(bildirimler, okunmamisSayisi = bildirimler.count { !it.okundu })
        } }
    }

    /** Kapalı fazın türleri (ör. Faz 1'de yorum) ne listede ne tercihlerde görünür; veri silinmez. */
    private suspend fun kapaliTurler(): List<String> {
        val aktif = fazlar.aktif()
        return BildirimTuru.entries.filterNot { aktif.kapsar(it.faz) }.map { it.name }
    }

    suspend fun okunmamisSayisi(alici: UUID): Int = liste(alici).okunmamisSayisi

    suspend fun okunduSay(alici: UUID) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) { BildirimTablosu.update({ BildirimTablosu.alici eq alici }) { it[okundu] = true } }
        }
    }

    suspend fun tercihler(kullaniciId: UUID): BildirimTercihleri {
        val kapali = kapaliTurler()
        return withContext(Dispatchers.IO) {
            transaction(veritabani) { BildirimTercihleri(pushTercihleri(kullaniciId).filterKeys { it.name !in kapali }) }
        }
    }

    suspend fun tercihleriKaydet(kullaniciId: UUID, tercihler: BildirimTercihleri) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                tercihler.push.forEach { (tur, acik) ->
                    BildirimTercihiTablosu.upsert {
                        it[BildirimTercihiTablosu.kullaniciId] = kullaniciId
                        it[BildirimTercihiTablosu.tur] = tur.name
                        it[push] = acik
                    }
                }
            }
        }
    }

    /** Aynı token başka kullanıcıdaysa (cihaz el değiştirdi) bu kullanıcıya geçer. */
    suspend fun cihazKaydet(kullaniciId: UUID, istek: CihazKaydiIstegi) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                PushCihaziTablosu.upsert {
                    it[token] = istek.token
                    it[PushCihaziTablosu.kullaniciId] = kullaniciId
                    it[platform] = istek.platform.name
                    it[saatDilimi] = istek.saatDilimi
                    it[guncellenme] = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)
                }
            }
        }
    }

    private companion object {
        const val SON_BILDIRIM = 200
    }
}

/** Satırı olmayan tür için push açıktır. */
internal fun JdbcTransaction.pushTercihleri(kullaniciId: UUID): Map<BildirimTuru, Boolean> {
    val kayitli = BildirimTercihiTablosu.selectAll().where { BildirimTercihiTablosu.kullaniciId eq kullaniciId }
        .associate { it[BildirimTercihiTablosu.tur] to it[BildirimTercihiTablosu.push] }
    return BildirimTuru.entries.associateWith { kayitli[it.name] ?: true }
}

internal object BildirimTablosu : Table("bildirim") {
    val id = javaUUID("id")
    val alici = javaUUID("alici")
    val tur = text("tur")
    val aktor = javaUUID("aktor").nullable()
    val gunlukKaydiId = javaUUID("gunluk_kaydi_id").nullable()
    val yorumId = javaUUID("yorum_id").nullable()
    val olusturulma = timestampWithTimeZone("olusturulma")
    val okundu = bool("okundu").default(false)
    val pushDurumu = text("push_durumu").default(PushDurumu.BEKLIYOR.name)
    val pushZamani = timestampWithTimeZone("push_zamani").nullable()
    override val primaryKey = PrimaryKey(id)
}

internal enum class PushDurumu { BEKLIYOR, GONDERILDI, ATLANDI }

internal object BildirimTercihiTablosu : Table("bildirim_tercihi") {
    val kullaniciId = javaUUID("kullanici_id")
    val tur = text("tur")
    val push = bool("push")
    override val primaryKey = PrimaryKey(kullaniciId, tur)
}

internal object PushCihaziTablosu : Table("push_cihazi") {
    val token = text("token")
    val kullaniciId = javaUUID("kullanici_id")
    val platform = text("platform")
    val saatDilimi = text("saat_dilimi")
    val guncellenme = timestampWithTimeZone("guncellenme")
    override val primaryKey = PrimaryKey(token)
}
