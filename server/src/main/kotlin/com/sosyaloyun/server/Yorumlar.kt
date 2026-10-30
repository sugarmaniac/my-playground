package com.sosyaloyun.server

import com.sosyaloyun.shared.BildirimTuru
import com.sosyaloyun.shared.Yorum
import com.sosyaloyun.shared.YorumKurallari
import com.sosyaloyun.shared.YorumListesi
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
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Günlük kaydı yorumları (Faz 2, #47). Kaydı görme yetkisi çağıran tarafından denetlenir. İzleyenle arasında engel
 * olanların ve izleyenin sessize aldıklarının yorumları görünmez. Yorumu yazarı ya da kaydın sahibi silebilir.
 */
class Yorumlar(private val veritabani: Database, private val saat: Clock) {
    sealed interface YazmaSonucu {
        data class Yazildi(val yorum: Yorum) : YazmaSonucu
        data object Gecersiz : YazmaSonucu

        /** Yorum yasağı [bitis]'e kadar sürüyor (#53). */
        data class Yasakli(val bitis: Instant) : YazmaSonucu
    }

    suspend fun yaz(yazar: UUID, kayitId: UUID, hamMetin: String): YazmaSonucu {
        val metin = hamMetin.trim()
        if (metin.isEmpty() || metin.length > YorumKurallari.EN_FAZLA) return YazmaSonucu.Gecersiz
        return withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val simdi = simdi()
                val yasak = KullaniciTablosu.select(KullaniciTablosu.yorumYasagiBitisi).where { KullaniciTablosu.id eq yazar }
                    .single()[KullaniciTablosu.yorumYasagiBitisi]
                if (yasak != null && yasak.isAfter(simdi)) return@transaction YazmaSonucu.Yasakli(yasak.anlik())
                val id = UUID.randomUUID()
                YorumTablosu.insert {
                    it[YorumTablosu.id] = id
                    it[gunlukKaydiId] = kayitId
                    it[kullaniciId] = yazar
                    it[YorumTablosu.metin] = metin
                    it[olusturulma] = simdi
                }
                val sahip = GunlukKaydiTablosu.selectAll().where { GunlukKaydiTablosu.id eq kayitId }.single()[GunlukKaydiTablosu.kullaniciId]
                with(BildirimOlaylari) { bildir(sahip, BildirimTuru.YORUM, yazar, simdi, kayitId, yorumId = id) }
                val kisi = KullaniciTablosu.selectAll().where { KullaniciTablosu.id eq yazar }.single().kullaniciOzeti()!!
                YazmaSonucu.Yazildi(Yorum(id.toString(), kisi, metin, simdi.anlik(), silebilirim = true, benim = true))
            }
        }
    }

    suspend fun liste(kayitId: UUID, kayitSahibi: UUID, izleyen: UUID, imlec: String?): YorumListesi = withContext(Dispatchers.IO) {
        val baslangic = Imlec.baslangic(imlec)
        transaction(veritabani) {
            val gizli = engellileri(izleyen) + sessizeAldiklari(izleyen)
            val satirlar = YorumTablosu.join(KullaniciTablosu, JoinType.INNER, YorumTablosu.kullaniciId, KullaniciTablosu.id)
                .selectAll()
                .where { (YorumTablosu.gunlukKaydiId eq kayitId) and (YorumTablosu.kullaniciId notInList gizli) }
                .orderBy(YorumTablosu.olusturulma to SortOrder.ASC, YorumTablosu.id to SortOrder.ASC)
                .limit(SAYFA + 1)
                .offset(baslangic.toLong())
                .toList()
            val yorumlar = satirlar.take(SAYFA).mapNotNull { satir ->
                val kisi = satir.kullaniciOzeti() ?: return@mapNotNull null
                val yazar = satir[YorumTablosu.kullaniciId]
                Yorum(
                    id = satir[YorumTablosu.id].toString(),
                    kisi = kisi,
                    metin = satir[YorumTablosu.metin],
                    zaman = satir[YorumTablosu.olusturulma].anlik(),
                    silebilirim = yazar == izleyen || kayitSahibi == izleyen,
                    benim = yazar == izleyen,
                )
            }
            YorumListesi(yorumlar, Imlec.sonraki(satirlar.size, baslangic, SAYFA))
        }
    }

    /** Yorumun yazarı ya da kaydın sahibi siler; bildirimi yorumla birlikte silinir. Silinecek yorum yoksa false. */
    suspend fun sil(isteyen: UUID, yorumId: UUID): Boolean = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val satir = YorumTablosu.join(GunlukKaydiTablosu, JoinType.INNER, YorumTablosu.gunlukKaydiId, GunlukKaydiTablosu.id)
                .selectAll().where { YorumTablosu.id eq yorumId }.singleOrNull() ?: return@transaction false
            if (satir[YorumTablosu.kullaniciId] != isteyen && satir[GunlukKaydiTablosu.kullaniciId] != isteyen) return@transaction false
            YorumTablosu.deleteWhere { id eq yorumId } > 0
        }
    }

    private fun simdi(): OffsetDateTime = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)

    private companion object {
        const val SAYFA = 50
    }
}

private fun OffsetDateTime.anlik(): Instant = toInstant().let { Instant.fromEpochSeconds(it.epochSecond, it.nano) }

internal object YorumTablosu : Table("yorum") {
    val id = javaUUID("id")
    val gunlukKaydiId = javaUUID("gunluk_kaydi_id")
    val kullaniciId = javaUUID("kullanici_id")
    val metin = text("metin")
    val olusturulma = timestampWithTimeZone("olusturulma")
    override val primaryKey = PrimaryKey(id)
}
