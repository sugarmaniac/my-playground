package com.sosyaloyun.server

import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.IncelemeListesi
import com.sosyaloyun.shared.OyunIncelemesi
import com.sosyaloyun.shared.OyunPuanlari
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.avg
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Bir oyunun topluluk tarafı: topluluk puanı, arkadaş ortalaması ve incelemeler. */
class Topluluk(private val veritabani: Database, private val ayarlar: Ayarlar) {
    suspend fun puanlar(izleyen: UUID, oyunId: UUID): OyunPuanlari {
        val esik = ayarlar.oku(Ayar.ToplulukPuaniEsigi)
        return withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val k = KutuphaneGirisiTablosu
                val ortalama = k.puan.avg(4)
                val sayi = Count(k.puan)
                val topluluk = k.select(ortalama, sayi).where { (k.oyunId eq oyunId) and k.puan.isNotNull() }.single()
                val arkadaslar = arkadaslari(izleyen)
                val arkadasOrtalamasi = if (arkadaslar.isEmpty()) null else
                    k.select(ortalama).where { (k.oyunId eq oyunId) and k.puan.isNotNull() and (k.kullaniciId inList arkadaslar) }
                        .single()[ortalama]?.toDouble()
                OyunPuanlari(
                    toplulukPuani = topluluk[ortalama]?.toDouble()?.takeIf { topluluk[sayi] >= esik },
                    arkadasOrtalamasi = arkadasOrtalamasi,
                    oynayanArkadasSayisi = oynayanlar(oyunId, arkadaslar).size,
                )
            }
        }
    }

    /**
     * Oyunun incelemeleri (metni olan günlük kayıtları): önce arkadaşlarınki, sonra diğerleri, her grupta yeniden eskiye.
     * İzleyenin kendi kayıtları detayda ayrıca gösterildiği için dahil değil; görünürlük uygulanır.
     */
    suspend fun incelemeler(izleyen: UUID, oyunId: UUID, imlec: String?): IncelemeListesi = withContext(Dispatchers.IO) {
        val baslangic = Imlec.baslangic(imlec)
        transaction(veritabani) {
            val g = GunlukKaydiTablosu
            val arkadaslar = arkadaslari(izleyen)
            val takipEttikleri = TakipTablosu.select(TakipTablosu.takipEdilen).where { TakipTablosu.takipEden eq izleyen }
                .map { it[TakipTablosu.takipEdilen] }
            val gorunur = (g.gorunurluk eq Gorunurluk.HERKES.name) or
                ((g.gorunurluk eq Gorunurluk.TAKIPCILER.name) and (g.kullaniciId inList takipEttikleri)) or
                ((g.gorunurluk eq Gorunurluk.ARKADASLAR.name) and (g.kullaniciId inList arkadaslar))
            val arkadasOnce = Case().When(g.kullaniciId inList arkadaslar, intLiteral(0)).Else(intLiteral(1))
            val satirlar = g.join(KullaniciTablosu, JoinType.INNER, g.kullaniciId, KullaniciTablosu.id)
                .selectAll()
                .where { (g.oyunId eq oyunId) and g.inceleme.isNotNull() and (g.kullaniciId neq izleyen) and gorunur }
                .orderBy(arkadasOnce to SortOrder.ASC, g.olusturulma to SortOrder.DESC, g.id to SortOrder.ASC)
                .limit(SAYFA + 1)
                .offset(baslangic.toLong())
                .toList()
            IncelemeListesi(
                incelemeler = satirlar.take(SAYFA).mapNotNull { satir ->
                    val sahip = satir.kullaniciOzeti() ?: return@mapNotNull null
                    OyunIncelemesi(satir.gunlukKaydi(), sahip, arkadas = satir[g.kullaniciId] in arkadaslar)
                },
                sonrakiImlec = Imlec.sonraki(satirlar.size, baslangic, SAYFA),
            )
        }
    }

    private fun JdbcTransaction.arkadaslari(kullaniciId: UUID): List<UUID> =
        ArkadaslikTablosu.selectAll()
            .where { (ArkadaslikTablosu.kullaniciA eq kullaniciId) or (ArkadaslikTablosu.kullaniciB eq kullaniciId) }
            .map { if (it[ArkadaslikTablosu.kullaniciA] == kullaniciId) it[ArkadaslikTablosu.kullaniciB] else it[ArkadaslikTablosu.kullaniciA] }

    /** Oyunu oynayanlar: kütüphane girişinde durumu olanlar ya da günlük kaydı girenler. */
    private fun JdbcTransaction.oynayanlar(oyunId: UUID, kisiler: List<UUID>): Set<UUID> {
        if (kisiler.isEmpty()) return emptySet()
        val k = KutuphaneGirisiTablosu
        val g = GunlukKaydiTablosu
        val durumu = k.select(k.kullaniciId).where { (k.oyunId eq oyunId) and k.durum.isNotNull() and (k.kullaniciId inList kisiler) }
            .map { it[k.kullaniciId] }
        val kaydi = g.select(g.kullaniciId).where { (g.oyunId eq oyunId) and (g.kullaniciId inList kisiler) }.withDistinct()
            .map { it[g.kullaniciId] }
        return (durumu + kaydi).toSet()
    }

    private companion object {
        const val SAYFA = 20
    }
}
