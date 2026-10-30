package com.sosyaloyun.server

import com.sosyaloyun.shared.BildirimTuru
import com.sosyaloyun.shared.ItirazDurumu
import com.sosyaloyun.shared.ItirazKurallari
import com.sosyaloyun.shared.ItirazSonucu
import com.sosyaloyun.shared.YaptirimOzeti
import com.sosyaloyun.shared.YaptirimTuru
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** Yönetim panelinde karar bekleyen itiraz. */
data class BekleyenItiraz(val id: UUID, val kullaniciAdi: String, val yaptirim: YaptirimTuru, val yaptirimZamani: OffsetDateTime, val metin: String, val zaman: OffsetDateTime)

/**
 * İtirazlar (Faz 2, #53): kullanıcı son yaptırımına bir kez itiraz eder; askıdaki ya da banlı hesap giriş ekranından,
 * reddedilen girişte verilen anahtarla. Ekip itirazı kabul ederse yaptırım kalkar, ederse de etmezse de kalıp mesaj gider.
 */
class Itirazlar(private val veritabani: Database, private val saat: Clock) {
    sealed interface Sonuc {
        data object Tamam : Sonuc
        data object Gecersiz : Sonuc
    }

    suspend fun durum(kullanici: UUID): ItirazDurumu = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val yaptirim = sonYaptirim(kullanici) ?: return@transaction ItirazDurumu()
            val itiraz = ItirazTablosu.selectAll().where { ItirazTablosu.yaptirimId eq yaptirim[YaptirimTablosu.id] }.singleOrNull()
            ItirazDurumu(yaptirim.ozet(), itiraz?.let { ItirazSonucu.valueOf(it[ItirazTablosu.durum]) })
        }
    }

    suspend fun itirazEt(kullanici: UUID, metin: String): Sonuc = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val yaptirim = sonYaptirim(kullanici) ?: return@transaction Sonuc.Gecersiz
            yaz(yaptirim[YaptirimTablosu.id], metin)
        }
    }

    /**
     * Askıdaki ya da banlı hesabın girişi engelleyen en son yaptırımı için yeni bir giriş ekranı anahtarı üretir; önceki
     * anahtar geçersizleşir. Sonradan gelen bir uyarı ya da içerik kaldırma bu yaptırımın itirazını engellemez.
     */
    suspend fun girisAnahtari(kullanici: UUID): String? = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val yaptirim = etkinYaptirimlar(kullanici).firstOrNull { YaptirimTuru.valueOf(it[YaptirimTablosu.tur]) in GIRIS_ENGELLEYEN }
                ?: return@transaction null
            val anahtar = UUID.randomUUID().toString()
            YaptirimTablosu.update({ YaptirimTablosu.id eq yaptirim[YaptirimTablosu.id] }) { it[itirazAnahtari] = ozet(anahtar) }
            anahtar
        }
    }

    suspend fun girisIleItirazEt(anahtar: String, metin: String): Sonuc = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val yaptirim = YaptirimTablosu.selectAll()
                .where { (YaptirimTablosu.itirazAnahtari eq ozet(anahtar)) and (YaptirimTablosu.kaldirildi eq false) }
                .singleOrNull() ?: return@transaction Sonuc.Gecersiz
            yaz(yaptirim[YaptirimTablosu.id], metin)
        }
    }

    suspend fun bekleyenler(): List<BekleyenItiraz> = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            ItirazTablosu.join(YaptirimTablosu, JoinType.INNER, ItirazTablosu.yaptirimId, YaptirimTablosu.id)
                .join(KullaniciTablosu, JoinType.INNER, YaptirimTablosu.kullaniciId, KullaniciTablosu.id)
                .selectAll().where { ItirazTablosu.durum eq ItirazSonucu.BEKLIYOR.name }
                .orderBy(ItirazTablosu.olusturulma to SortOrder.ASC)
                .map {
                    BekleyenItiraz(
                        it[ItirazTablosu.id], it[KullaniciTablosu.kullaniciAdi].orEmpty(), YaptirimTuru.valueOf(it[YaptirimTablosu.tur]),
                        it[YaptirimTablosu.olusturulma], it[ItirazTablosu.metin], it[ItirazTablosu.olusturulma],
                    )
                }
        }
    }

    /**
     * Bekleyen itiraza karar verir. Kabulde yaptırım kalkar: askı ve ban biter, yorum yasağı kalkar; kaldırılan içerik geri
     * gelmez, kayıt kaldırıldı olarak işaretlenir. İtiraz yoksa null; varsa itiraz edenin kullanıcı adı (denetim kaydı için).
     */
    suspend fun karar(itirazId: UUID, kabul: Boolean): String? = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val satir = ItirazTablosu.join(YaptirimTablosu, JoinType.INNER, ItirazTablosu.yaptirimId, YaptirimTablosu.id)
                .join(KullaniciTablosu, JoinType.INNER, YaptirimTablosu.kullaniciId, KullaniciTablosu.id)
                .selectAll().where { (ItirazTablosu.id eq itirazId) and (ItirazTablosu.durum eq ItirazSonucu.BEKLIYOR.name) }
                .singleOrNull() ?: return@transaction null
            val kullanici = satir[YaptirimTablosu.kullaniciId]
            val simdi = simdi()
            ItirazTablosu.update({ ItirazTablosu.id eq itirazId }) {
                it[durum] = (if (kabul) ItirazSonucu.KABUL_EDILDI else ItirazSonucu.REDDEDILDI).name
                it[kararZamani] = simdi
            }
            if (kabul) {
                YaptirimTablosu.update({ YaptirimTablosu.id eq satir[YaptirimTablosu.id] }) { it[kaldirildi] = true }
                // Yalnızca bu yaptırım kalkar: aynı türden başka etkin yaptırım varsa hesap onun sonuna göre kalır.
                val kalanlar = etkinYaptirimlar(kullanici)
                fun enGecBitis(tur: YaptirimTuru) = kalanlar.filter { YaptirimTuru.valueOf(it[YaptirimTablosu.tur]) == tur }
                    .mapNotNull { it[YaptirimTablosu.bitis] }.maxOrNull()
                when (YaptirimTuru.valueOf(satir[YaptirimTablosu.tur])) {
                    YaptirimTuru.ASKI -> KullaniciTablosu.update({ KullaniciTablosu.id eq kullanici }) { it[askininBitisi] = enGecBitis(YaptirimTuru.ASKI) }
                    YaptirimTuru.BAN -> KullaniciTablosu.update({ KullaniciTablosu.id eq kullanici }) {
                        it[banli] = kalanlar.any { k -> YaptirimTuru.valueOf(k[YaptirimTablosu.tur]) == YaptirimTuru.BAN }
                    }
                    YaptirimTuru.YORUM_YASAGI -> KullaniciTablosu.update({ KullaniciTablosu.id eq kullanici }) { it[yorumYasagiBitisi] = enGecBitis(YaptirimTuru.YORUM_YASAGI) }
                    YaptirimTuru.ICERIK_KALDIRMA, YaptirimTuru.UYARI -> Unit
                }
            }
            with(BildirimOlaylari) { kalipMesaj(kullanici, if (kabul) BildirimTuru.ITIRAZ_KABUL_EDILDI else BildirimTuru.ITIRAZ_REDDEDILDI, simdi) }
            satir[KullaniciTablosu.kullaniciAdi].orEmpty()
        }
    }

    private fun JdbcTransaction.yaz(yaptirimId: UUID, hamMetin: String): Sonuc {
        val metin = hamMetin.trim()
        if (metin.isEmpty() || metin.length > ItirazKurallari.EN_FAZLA) return Sonuc.Gecersiz
        // Tekil yaptirim_id: aynı yaptırıma ikinci itiraz (eşzamanlı olanlar dahil) yazılmaz.
        val yazildi = ItirazTablosu.insertIgnore {
            it[id] = UUID.randomUUID()
            it[ItirazTablosu.yaptirimId] = yaptirimId
            it[ItirazTablosu.metin] = metin
            it[olusturulma] = simdi()
        }.insertedCount > 0
        return if (yazildi) Sonuc.Tamam else Sonuc.Gecersiz
    }

    /** Kalkmamış en son yaptırım, hâlâ etkinse. */
    private fun JdbcTransaction.sonYaptirim(kullanici: UUID): ResultRow? =
        YaptirimTablosu.selectAll()
            .where { (YaptirimTablosu.kullaniciId eq kullanici) and (YaptirimTablosu.kaldirildi eq false) }
            .orderBy(YaptirimTablosu.olusturulma to SortOrder.DESC)
            .limit(1).singleOrNull()
            ?.takeIf { etkinMi(it) }

    /** Kalkmamış ve hâlâ etkin yaptırımlar, yeniden eskiye. */
    private fun JdbcTransaction.etkinYaptirimlar(kullanici: UUID): List<ResultRow> =
        YaptirimTablosu.selectAll()
            .where { (YaptirimTablosu.kullaniciId eq kullanici) and (YaptirimTablosu.kaldirildi eq false) }
            .orderBy(YaptirimTablosu.olusturulma to SortOrder.DESC)
            .filter { etkinMi(it) }

    /** Süresi dolmuş askı ve yorum yasağı etkin değildir; uyarı ve içerik kaldırma 30 gün itiraz edilebilir. */
    private fun etkinMi(satir: ResultRow): Boolean {
        val bitis = satir[YaptirimTablosu.bitis]
        return when (YaptirimTuru.valueOf(satir[YaptirimTablosu.tur])) {
            YaptirimTuru.BAN -> true
            YaptirimTuru.ASKI, YaptirimTuru.YORUM_YASAGI -> bitis != null && bitis.isAfter(simdi())
            YaptirimTuru.UYARI, YaptirimTuru.ICERIK_KALDIRMA -> satir[YaptirimTablosu.olusturulma].plusDays(ITIRAZ_SURESI_GUN).isAfter(simdi())
        }
    }

    private fun ResultRow.ozet() = YaptirimOzeti(
        YaptirimTuru.valueOf(this[YaptirimTablosu.tur]),
        this[YaptirimTablosu.olusturulma].anlik(),
        this[YaptirimTablosu.bitis]?.anlik(),
    )

    private fun simdi(): OffsetDateTime = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)

    private companion object {
        const val ITIRAZ_SURESI_GUN = 30L
        val GIRIS_ENGELLEYEN = setOf(YaptirimTuru.ASKI, YaptirimTuru.BAN)

        fun ozet(anahtar: String): String =
            MessageDigest.getInstance("SHA-256").digest(anahtar.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

private fun OffsetDateTime.anlik(): Instant = toInstant().let { Instant.fromEpochSeconds(it.epochSecond, it.nano) }

internal object YaptirimTablosu : Table("yaptirim") {
    val id = javaUUID("id")
    val kullaniciId = javaUUID("kullanici_id")
    val tur = text("tur")
    val olusturulma = timestampWithTimeZone("olusturulma")
    val bitis = timestampWithTimeZone("bitis").nullable()
    val kaldirildi = bool("kaldirildi").default(false)
    val itirazAnahtari = text("itiraz_anahtari").nullable()
    override val primaryKey = PrimaryKey(id)
}

internal object ItirazTablosu : Table("itiraz") {
    val id = javaUUID("id")
    val yaptirimId = javaUUID("yaptirim_id")
    val metin = text("metin")
    val olusturulma = timestampWithTimeZone("olusturulma")
    val durum = text("durum").default(ItirazSonucu.BEKLIYOR.name)
    val kararZamani = timestampWithTimeZone("karar_zamani").nullable()
    override val primaryKey = PrimaryKey(id)
}
