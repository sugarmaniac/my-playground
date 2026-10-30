package com.sosyaloyun.server

import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.KutuphaneGirisi
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

/** Kullanıcıların kütüphane girişleri; kurallar (`KutuphaneKurallari`) çağıran tarafından önceden denetlenir. */
class Kutuphane(private val veritabani: Database, private val saat: Clock) {
    suspend fun giris(kullaniciId: UUID, oyunId: UUID): KutuphaneGirisi? = withContext(Dispatchers.IO) {
        transaction(veritabani) { satir(kullaniciId, oyunId)?.girisi() }
    }

    /** Boş giriş, günlük kaydı yoksa silinir (null döner); aksi halde tek giriş oluşturulur ya da güncellenir. */
    suspend fun kaydet(kullaniciId: UUID, oyunId: UUID, giris: KutuphaneGirisi): KutuphaneGirisi? = withContext(Dispatchers.IO) {
        val simdi = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)
        transaction(veritabani) {
            val kosul = (KutuphaneGirisiTablosu.kullaniciId eq kullaniciId) and (KutuphaneGirisiTablosu.oyunId eq oyunId)
            // Günlük kaydı olan giriş silinmez, durumsuz kalır: günlük kayıtları her zaman bir girişe bağlıdır.
            val gunlukVar = GunlukKaydiTablosu.selectAll()
                .where { (GunlukKaydiTablosu.kullaniciId eq kullaniciId) and (GunlukKaydiTablosu.oyunId eq oyunId) }
                .any()
            if (giris.bos && !gunlukVar) {
                KutuphaneGirisiTablosu.deleteWhere { kosul }
                return@transaction null
            }
            val onceki = satir(kullaniciId, oyunId)
            val durumDegisti = onceki?.get(KutuphaneGirisiTablosu.durum) != giris.durum?.name
            KutuphaneGirisiTablosu.upsert {
                it[KutuphaneGirisiTablosu.kullaniciId] = kullaniciId
                it[KutuphaneGirisiTablosu.oyunId] = oyunId
                it[durum] = giris.durum?.name
                it[puan] = giris.puan?.toShort()
                it[begendim] = giris.begendim
                it[istekListesinde] = giris.istekListesinde
                it[yuzdeYuz] = giris.yuzdeYuz
                it[durumDegisme] = if (durumDegisti) simdi else onceki?.get(durumDegisme)
                it[guncellenme] = simdi
            }
            // Akışa yalnızca durumun Oynuyorum olması düşer; puan, istek listesi ve diğer durumlar düşmez.
            if (durumDegisti && giris.durum == Durum.OYNUYORUM) with(AkisOlaylari) { oynamayaBasladi(kullaniciId, oyunId, simdi) }
            giris.takeUnless { it.bos && !gunlukVar }
        }
    }

    /** Akış kartından tek dokunuş: diğer işaretlere dokunmadan istek listesine ekler; giriş yoksa açılır. Akışa düşmez. */
    suspend fun istekListesineEkle(kullaniciId: UUID, oyunId: UUID) {
        val simdi = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                KutuphaneGirisiTablosu.insertIgnore {
                    it[KutuphaneGirisiTablosu.kullaniciId] = kullaniciId
                    it[KutuphaneGirisiTablosu.oyunId] = oyunId
                    it[guncellenme] = simdi
                }
                KutuphaneGirisiTablosu.update({ (KutuphaneGirisiTablosu.kullaniciId eq kullaniciId) and (KutuphaneGirisiTablosu.oyunId eq oyunId) }) {
                    it[istekListesinde] = true
                    it[guncellenme] = simdi
                }
            }
        }
    }

    private fun satir(kullaniciId: UUID, oyunId: UUID): ResultRow? =
        KutuphaneGirisiTablosu.selectAll()
            .where { (KutuphaneGirisiTablosu.kullaniciId eq kullaniciId) and (KutuphaneGirisiTablosu.oyunId eq oyunId) }
            .singleOrNull()

    private fun ResultRow.girisi() = KutuphaneGirisi(
        durum = this[KutuphaneGirisiTablosu.durum]?.let(Durum::valueOf),
        puan = this[KutuphaneGirisiTablosu.puan]?.toInt(),
        begendim = this[KutuphaneGirisiTablosu.begendim],
        istekListesinde = this[KutuphaneGirisiTablosu.istekListesinde],
        yuzdeYuz = this[KutuphaneGirisiTablosu.yuzdeYuz],
    )
}

internal object KutuphaneGirisiTablosu : Table("kutuphane_girisi") {
    val kullaniciId = javaUUID("kullanici_id")
    val oyunId = javaUUID("oyun_id")
    val durum = text("durum").nullable()
    val puan = short("puan").nullable()
    val begendim = bool("begendim")
    val istekListesinde = bool("istek_listesinde")
    val yuzdeYuz = bool("yuzde_yuz")
    val durumDegisme = timestampWithTimeZone("durum_degisme").nullable()
    val guncellenme = timestampWithTimeZone("guncellenme")
    override val primaryKey = PrimaryKey(kullaniciId, oyunId)
}
