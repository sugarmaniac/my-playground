package com.sosyaloyun.server

import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydiDetayi
import com.sosyaloyun.shared.GunlukOgesi
import com.sosyaloyun.shared.PaylasilanProfil
import com.sosyaloyun.shared.PaylasimLinkleri
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Web sitesinin paylaşım sayfaları (ADR 0004): oturumsuz izleyen yalnızca Herkes görünürlüğündeki kayıtları görür.
 * Banlı ya da askıdaki hesabın içeriği, moderasyonla kaldırılan içerik gibi hiç yokmuş sayılır.
 */
class Paylasim(
    private val veritabani: Database,
    private val katalog: Katalog,
    private val profiller: Profiller,
    private val begeniler: Begeniler,
    private val saat: Clock,
) {
    suspend fun kayit(kayitId: UUID): GunlukKaydiDetayi? {
        val (kayit, sahip) = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                GunlukKaydiTablosu.join(KullaniciTablosu, JoinType.INNER, GunlukKaydiTablosu.kullaniciId, KullaniciTablosu.id)
                    .selectAll()
                    .where { (GunlukKaydiTablosu.id eq kayitId) and (GunlukKaydiTablosu.gorunurluk eq Gorunurluk.HERKES.name) }
                    .singleOrNull()
                    ?.takeIf { it.paylasilabilir(simdi()) }
                    ?.let { satir -> satir.kullaniciOzeti()?.let { satir.gunlukKaydi() to it } }
            }
        } ?: return null
        val oyunId = UUID.fromString(kayit.oyunId)
        // Oturumsuz izleyen hiçbir kaydı beğenmiş olamaz; yalnızca beğeni sayısı eklenir.
        return GunlukKaydiDetayi(begeniler.ekle(BEGENMEYEN, kayit), katalog.ozetleri(listOf(oyunId)).getValue(oyunId), sahip)
    }

    suspend fun profil(kullaniciId: UUID): PaylasilanProfil? {
        val kayitlar = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val sahip = KullaniciTablosu.selectAll().where { KullaniciTablosu.id eq kullaniciId }.singleOrNull()
                if (sahip == null || !sahip.paylasilabilir(simdi())) return@transaction null
                GunlukKaydiTablosu.selectAll()
                    .where { (GunlukKaydiTablosu.kullaniciId eq kullaniciId) and (GunlukKaydiTablosu.gorunurluk eq Gorunurluk.HERKES.name) }
                    .orderBy(
                        GunlukKaydiTablosu.tarih to SortOrder.DESC,
                        GunlukKaydiTablosu.olusturulma to SortOrder.DESC,
                        GunlukKaydiTablosu.id to SortOrder.DESC,
                    )
                    .limit(PaylasimLinkleri.PROFILDEKI_KAYIT_SAYISI)
                    .map { it.gunlukKaydi() }
            }
        } ?: return null
        val sayfa = profiller.sayfa(kullaniciId) ?: return null
        val oyunlar = katalog.ozetleri(kayitlar.map { UUID.fromString(it.oyunId) })
        return PaylasilanProfil(
            profil = sayfa,
            sonKayitlar = begeniler.ekle(BEGENMEYEN, kayitlar).map { GunlukOgesi(it, oyunlar.getValue(UUID.fromString(it.oyunId))) },
        )
    }

    private fun simdi(): OffsetDateTime = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)

    private companion object {
        /** Hiçbir kullanıcının kimliği olmayan sabit; beğeni sayısını "beğendim" olmadan almak için. */
        val BEGENMEYEN: UUID = UUID(0, 0)
    }
}

/** Banlı ya da askısı sürmekte olan hesabın içeriği paylaşılmaz. */
private fun ResultRow.paylasilabilir(simdi: OffsetDateTime): Boolean =
    !this[KullaniciTablosu.banli] && this[KullaniciTablosu.askininBitisi]?.isAfter(simdi) != true
