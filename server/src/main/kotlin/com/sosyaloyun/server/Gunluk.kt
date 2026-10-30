package com.sosyaloyun.server

import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Sum
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** Günlük kayıtları; alan kuralları (`GunlukKurallari`) çağıran tarafından önceden denetlenir. */
class Gunluk(private val veritabani: Database, private val saat: Clock) {
    /** Yeni kayıt; kütüphane girişi yoksa durumsuz olarak açılır, puan girişten kopyalanır. */
    suspend fun gir(kullaniciId: UUID, oyunId: UUID, istek: GunlukKaydiIstegi): GunlukKaydi = withContext(Dispatchers.IO) {
        val simdi = simdi()
        transaction(veritabani) {
            KutuphaneGirisiTablosu.insertIgnore {
                it[KutuphaneGirisiTablosu.kullaniciId] = kullaniciId
                it[KutuphaneGirisiTablosu.oyunId] = oyunId
                it[guncellenme] = simdi
            }
            val puan = KutuphaneGirisiTablosu.select(KutuphaneGirisiTablosu.puan)
                .where { (KutuphaneGirisiTablosu.kullaniciId eq kullaniciId) and (KutuphaneGirisiTablosu.oyunId eq oyunId) }
                .single()[KutuphaneGirisiTablosu.puan]
            val id = UUID.randomUUID()
            GunlukKaydiTablosu.insert {
                it[GunlukKaydiTablosu.id] = id
                it[GunlukKaydiTablosu.kullaniciId] = kullaniciId
                it[GunlukKaydiTablosu.oyunId] = oyunId
                it[GunlukKaydiTablosu.puan] = puan
                it[olusturulma] = simdi
                alanlar(it, istek, simdi)
                it[tarih] = siralamaTarihi(istek, simdi)
            }
            // Sessiz kayıtların olayı da yazılır; akış sessiz kayıtları okurken eler (sonradan sessizlik kaldırılabilir).
            with(AkisOlaylari) { gunlukKaydiGirildi(kullaniciId, oyunId, id, simdi) }
            sonPlatformuYaz(kullaniciId, istek)
            kayit(id)!!
        }
    }

    /** Puan kopyası ve oluşturulma anı değişmez. Kayıt kullanıcının değilse null. */
    suspend fun duzenle(kullaniciId: UUID, kayitId: UUID, istek: GunlukKaydiIstegi): GunlukKaydi? = withContext(Dispatchers.IO) {
        val simdi = simdi()
        transaction(veritabani) {
            val kosul = (GunlukKaydiTablosu.id eq kayitId) and (GunlukKaydiTablosu.kullaniciId eq kullaniciId)
            val olusturulma = GunlukKaydiTablosu.select(GunlukKaydiTablosu.olusturulma).where(kosul).singleOrNull()
                ?.get(GunlukKaydiTablosu.olusturulma) ?: return@transaction null
            val guncellenen = GunlukKaydiTablosu.update({ kosul }) {
                alanlar(it, istek, simdi)
                it[tarih] = siralamaTarihi(istek, olusturulma)
            }
            if (guncellenen == 0) return@transaction null
            sonPlatformuYaz(kullaniciId, istek)
            kayit(kayitId)
        }
    }

    suspend fun sil(kullaniciId: UUID, kayitId: UUID): Boolean = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            GunlukKaydiTablosu.deleteWhere { (id eq kayitId) and (GunlukKaydiTablosu.kullaniciId eq kullaniciId) } > 0
        }
    }

    data class OyunGunlugu(val kayitlar: List<GunlukKaydi>, val toplamSureDakika: Int?, val sonPlatform: String?)

    /** Kullanıcının bir oyundaki kayıtları (yeniden eskiye), toplam süresi ve son seçtiği platform. */
    suspend fun oyunGunlugu(kullaniciId: UUID, oyunId: UUID): OyunGunlugu = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val kosul = (GunlukKaydiTablosu.kullaniciId eq kullaniciId) and (GunlukKaydiTablosu.oyunId eq oyunId)
            val kayitlar = GunlukKaydiTablosu.selectAll()
                .where(kosul)
                .orderBy(GunlukKaydiTablosu.olusturulma to SortOrder.DESC, GunlukKaydiTablosu.id to SortOrder.DESC)
                .map { it.gunlukKaydi() }
            val toplam = Sum(GunlukKaydiTablosu.sureDakika, GunlukKaydiTablosu.sureDakika.columnType)
            val toplamSure = GunlukKaydiTablosu.select(toplam).where(kosul).single()[toplam]
            val sonPlatform = KullaniciTablosu.select(KullaniciTablosu.sonPlatform)
                .where { KullaniciTablosu.id eq kullaniciId }
                .single()[KullaniciTablosu.sonPlatform]
            OyunGunlugu(kayitlar, toplamSure, sonPlatform)
        }
    }

    private fun alanlar(it: UpdateBuilder<*>, istek: GunlukKaydiIstegi, simdi: OffsetDateTime) {
        it[GunlukKaydiTablosu.platform] = istek.platform?.trim()
        it[GunlukKaydiTablosu.baslama] = istek.baslama?.toJavaLocalDate()
        it[GunlukKaydiTablosu.bitis] = istek.bitis?.toJavaLocalDate()
        it[GunlukKaydiTablosu.sureDakika] = istek.sureDakika
        it[GunlukKaydiTablosu.inceleme] = istek.inceleme?.trim()?.takeIf { metin -> metin.isNotEmpty() }
        it[GunlukKaydiTablosu.spoiler] = istek.spoiler
        it[GunlukKaydiTablosu.gorunurluk] = istek.gorunurluk.name
        it[GunlukKaydiTablosu.sessiz] = istek.sessiz
        it[GunlukKaydiTablosu.guncellenme] = simdi
    }

    /** Günlüğün tarih sırası: bitirme, yoksa başlama, o da yoksa kaydın girildiği gün (Türkiye saati). */
    private fun siralamaTarihi(istek: GunlukKaydiIstegi, olusturulma: OffsetDateTime): java.time.LocalDate =
        istek.bitis?.toJavaLocalDate() ?: istek.baslama?.toJavaLocalDate() ?: olusturulma.atZoneSameInstant(TURKIYE).toLocalDate()

    private fun JdbcTransaction.sonPlatformuYaz(kullaniciId: UUID, istek: GunlukKaydiIstegi) {
        val platform = istek.platform?.trim() ?: return
        KullaniciTablosu.update({ KullaniciTablosu.id eq kullaniciId }) { it[sonPlatform] = platform }
    }

    private fun JdbcTransaction.kayit(id: UUID): GunlukKaydi? =
        GunlukKaydiTablosu.selectAll().where { GunlukKaydiTablosu.id eq id }.singleOrNull()?.gunlukKaydi()

    private fun simdi(): OffsetDateTime = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)
}

internal fun ResultRow.gunlukKaydi() = GunlukKaydi(
    id = this[GunlukKaydiTablosu.id].toString(),
    oyunId = this[GunlukKaydiTablosu.oyunId].toString(),
    platform = this[GunlukKaydiTablosu.platform],
    baslama = this[GunlukKaydiTablosu.baslama]?.toKotlinLocalDate(),
    bitis = this[GunlukKaydiTablosu.bitis]?.toKotlinLocalDate(),
    sureDakika = this[GunlukKaydiTablosu.sureDakika],
    puan = this[GunlukKaydiTablosu.puan]?.toInt(),
    inceleme = this[GunlukKaydiTablosu.inceleme],
    spoiler = this[GunlukKaydiTablosu.spoiler],
    gorunurluk = Gorunurluk.valueOf(this[GunlukKaydiTablosu.gorunurluk]),
    sessiz = this[GunlukKaydiTablosu.sessiz],
    olusturulma = this[GunlukKaydiTablosu.olusturulma].toInstant().let { Instant.fromEpochSeconds(it.epochSecond, it.nano) },
)

internal object GunlukKaydiTablosu : Table("gunluk_kaydi") {
    val id = javaUUID("id")
    val kullaniciId = javaUUID("kullanici_id")
    val oyunId = javaUUID("oyun_id")
    val platform = text("platform").nullable()
    val baslama = date("baslama").nullable()
    val bitis = date("bitis").nullable()
    val sureDakika = integer("sure_dakika").nullable()
    val puan = short("puan").nullable()
    val inceleme = text("inceleme").nullable()
    val spoiler = bool("spoiler")
    val gorunurluk = text("gorunurluk")
    val sessiz = bool("sessiz")
    val olusturulma = timestampWithTimeZone("olusturulma")
    val guncellenme = timestampWithTimeZone("guncellenme")
    val tarih = date("tarih")
    override val primaryKey = PrimaryKey(id)
}

/** Takvime bağlı hesaplar (günlük sırası, "bu yıl") Türkiye saatiyle yapılır; kullanıcılar Türkiye'de (ADR 0003). */
internal val TURKIYE: java.time.ZoneId = java.time.ZoneId.of("Europe/Istanbul")
