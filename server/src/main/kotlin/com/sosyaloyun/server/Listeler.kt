package com.sosyaloyun.server

import com.sosyaloyun.shared.Liste
import com.sosyaloyun.shared.ListeGorunurlugu
import com.sosyaloyun.shared.ListeIstegi
import com.sosyaloyun.shared.ListeKurallari
import com.sosyaloyun.shared.ListeOzeti
import com.sosyaloyun.shared.ListeSayfasi
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.or
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

/**
 * Oyun listeleri (Faz 2, #49). Bir listeyi sahibi her zaman görür; başkası yalnızca herkese açık listeyi, sahibiyle
 * arasında engel yoksa ve sahibi gizli profilse takipçisiyse görür.
 */
class Listeler(
    private val veritabani: Database,
    private val katalog: Katalog,
    private val sosyalGraf: SosyalGraf,
    private val saat: Clock,
) {
    sealed interface Sonuc {
        data class Tamam(val liste: Liste) : Sonuc
        data object Gecersiz : Sonuc
        data object Bulunamadi : Sonuc
    }

    suspend fun olustur(sahip: UUID, istek: ListeIstegi): Sonuc {
        val gecerli = dogrula(istek) ?: return Sonuc.Gecersiz
        val id = UUID.randomUUID()
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val simdi = simdi()
                ListeTablosu.insert {
                    it[ListeTablosu.id] = id
                    it[kullaniciId] = sahip
                    it[baslik] = gecerli.baslik
                    it[aciklama] = gecerli.aciklama
                    it[gorunurluk] = gecerli.gorunurluk.name
                    it[olusturulma] = simdi
                    it[guncellenme] = simdi
                }
                oyunlariYaz(id, gecerli.oyunlar)
            }
        }
        return getir(id, sahip)?.let { Sonuc.Tamam(it) } ?: Sonuc.Bulunamadi
    }

    suspend fun guncelle(sahip: UUID, id: UUID, istek: ListeIstegi): Sonuc {
        val gecerli = dogrula(istek) ?: return Sonuc.Gecersiz
        val guncellendi = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val sayi = ListeTablosu.update({ (ListeTablosu.id eq id) and (ListeTablosu.kullaniciId eq sahip) }) {
                    it[baslik] = gecerli.baslik
                    it[aciklama] = gecerli.aciklama
                    it[gorunurluk] = gecerli.gorunurluk.name
                    it[guncellenme] = simdi()
                }
                if (sayi == 0) return@transaction false
                ListeOyunuTablosu.deleteWhere { listeId eq id }
                oyunlariYaz(id, gecerli.oyunlar)
                true
            }
        }
        if (!guncellendi) return Sonuc.Bulunamadi
        return getir(id, sahip)?.let { Sonuc.Tamam(it) } ?: Sonuc.Bulunamadi
    }

    suspend fun sil(sahip: UUID, id: UUID): Boolean = withContext(Dispatchers.IO) {
        transaction(veritabani) { ListeTablosu.deleteWhere { (ListeTablosu.id eq id) and (kullaniciId eq sahip) } > 0 }
    }

    /** [izleyen] null ise oturumsuz (paylaşım sayfası): yalnızca herkese açık ve gizli olmayan profilin listesi. */
    suspend fun getir(id: UUID, izleyen: UUID?): Liste? {
        val satir = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                ListeTablosu.join(KullaniciTablosu, JoinType.INNER, ListeTablosu.kullaniciId, KullaniciTablosu.id)
                    .selectAll().where { ListeTablosu.id eq id }.singleOrNull()
            }
        } ?: return null
        val sahip = satir[ListeTablosu.kullaniciId]
        if (!gorebilir(izleyen, sahip, ListeGorunurlugu.valueOf(satir[ListeTablosu.gorunurluk]))) return null
        val sahipOzeti = satir.kullaniciOzeti() ?: return null
        val (oyunIdleri, begeni) = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val oyunlar = ListeOyunuTablosu.select(ListeOyunuTablosu.oyunId).where { ListeOyunuTablosu.listeId eq id }
                    .orderBy(ListeOyunuTablosu.sira to SortOrder.ASC).map { it[ListeOyunuTablosu.oyunId] }
                val sayi = ListeBegenisiTablosu.selectAll().where { ListeBegenisiTablosu.listeId eq id }.count().toInt()
                val begendim = izleyen != null && ListeBegenisiTablosu.selectAll()
                    .where { (ListeBegenisiTablosu.listeId eq id) and (ListeBegenisiTablosu.kullaniciId eq izleyen) }.any()
                oyunlar to (sayi to begendim)
            }
        }
        val ozetler = katalog.ozetleri(oyunIdleri)
        return Liste(
            id = id.toString(),
            sahip = sahipOzeti,
            baslik = satir[ListeTablosu.baslik],
            aciklama = satir[ListeTablosu.aciklama],
            gorunurluk = ListeGorunurlugu.valueOf(satir[ListeTablosu.gorunurluk]),
            oyunlar = oyunIdleri.mapNotNull(ozetler::get),
            begeniSayisi = begeni.first,
            begendim = begeni.second,
            benim = izleyen == sahip,
            guncellenme = satir[ListeTablosu.guncellenme].anlik(),
        )
    }

    /** Profilin Listeler sekmesi; sahibi kendi listelerinin hepsini görür. */
    suspend fun kullanicininListeleri(sahip: UUID, izleyen: UUID, imlec: String?): ListeSayfasi {
        if (izleyen != sahip && (sosyalGraf.engelliMi(izleyen, sahip) || sosyalGraf.icerikGizliMi(izleyen, sahip))) {
            return ListeSayfasi(emptyList(), null)
        }
        return sayfa(imlec) {
            val kosul = ListeTablosu.kullaniciId eq sahip
            if (izleyen == sahip) kosul else kosul and (ListeTablosu.gorunurluk eq ListeGorunurlugu.HERKES.name)
        }
    }

    /** Oyunu içeren herkese açık listeler; engelliler ve takip edilmeyen gizli profiller hariç. */
    suspend fun oyununListeleri(oyunId: UUID, izleyen: UUID, imlec: String?): ListeSayfasi {
        // Yalnızca Faz 2'de çağrılır; gizli profil kuralı her zaman uygulanır.
        return sayfa(imlec) {
            val takipEttikleri = TakipTablosu.select(TakipTablosu.takipEdilen).where { TakipTablosu.takipEden eq izleyen }
                .map { it[TakipTablosu.takipEdilen] }
            val gizliKosulu = (KullaniciTablosu.gizli eq false) or (ListeTablosu.kullaniciId inList takipEttikleri) or
                (ListeTablosu.kullaniciId eq izleyen)
            (ListeTablosu.gorunurluk eq ListeGorunurlugu.HERKES.name) and
                (ListeTablosu.id inSubQuery ListeOyunuTablosu.select(ListeOyunuTablosu.listeId).where { ListeOyunuTablosu.oyunId eq oyunId }) and
                (ListeTablosu.kullaniciId notInList engellileri(izleyen)) and gizliKosulu
        }
    }

    suspend fun begen(kullanici: UUID, id: UUID): Boolean {
        getir(id, kullanici) ?: return false
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                ListeBegenisiTablosu.insertIgnore {
                    it[listeId] = id
                    it[kullaniciId] = kullanici
                    it[olusturulma] = simdi()
                }
            }
        }
        return true
    }

    suspend fun begeniyiGeriAl(kullanici: UUID, id: UUID): Boolean {
        getir(id, kullanici) ?: return false
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                ListeBegenisiTablosu.deleteWhere { (listeId eq id) and (kullaniciId eq kullanici) }
            }
        }
        return true
    }

    private suspend fun gorebilir(izleyen: UUID?, sahip: UUID, gorunurluk: ListeGorunurlugu): Boolean = when {
        izleyen == sahip -> true
        gorunurluk == ListeGorunurlugu.BEN -> false
        izleyen == null -> !sosyalGraf.gizliMi(sahip)
        else -> !sosyalGraf.engelliMi(izleyen, sahip) && !sosyalGraf.icerikGizliMi(izleyen, sahip)
    }

    private suspend fun sayfa(imlec: String?, kosul: JdbcTransaction.() -> Op<Boolean>): ListeSayfasi {
        val baslangic = Imlec.baslangic(imlec)
        val (satirlar, oyunlar, begeniler) = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val satirlar = ListeTablosu.join(KullaniciTablosu, JoinType.INNER, ListeTablosu.kullaniciId, KullaniciTablosu.id)
                    .selectAll().where { kosul() }
                    .orderBy(ListeTablosu.guncellenme to SortOrder.DESC, ListeTablosu.id to SortOrder.ASC)
                    .limit(SAYFA + 1).offset(baslangic.toLong())
                    .toList()
                val idler = satirlar.map { it[ListeTablosu.id] }
                val oyunlar = ListeOyunuTablosu.selectAll().where { ListeOyunuTablosu.listeId inList idler }
                    .orderBy(ListeOyunuTablosu.sira to SortOrder.ASC)
                    .groupBy({ it[ListeOyunuTablosu.listeId] }, { it[ListeOyunuTablosu.oyunId] })
                val sayi = Count(ListeBegenisiTablosu.kullaniciId)
                val begeniler = ListeBegenisiTablosu.select(ListeBegenisiTablosu.listeId, sayi)
                    .where { ListeBegenisiTablosu.listeId inList idler }
                    .groupBy(ListeBegenisiTablosu.listeId)
                    .associate { it[ListeBegenisiTablosu.listeId] to it[sayi].toInt() }
                Triple(satirlar, oyunlar, begeniler)
            }
        }
        val ozetler = katalog.ozetleri(oyunlar.values.flatMap { it.take(ON_IZLEME) })
        val listeler = satirlar.take(SAYFA).mapNotNull { satir -> ozet(satir, oyunlar[satir[ListeTablosu.id]].orEmpty(), begeniler, ozetler) }
        return ListeSayfasi(listeler, Imlec.sonraki(satirlar.size, baslangic, SAYFA))
    }

    private fun ozet(
        satir: ResultRow,
        oyunIdleri: List<UUID>,
        begeniler: Map<UUID, Int>,
        ozetler: Map<UUID, com.sosyaloyun.shared.OyunOzeti>,
    ): ListeOzeti? {
        val id = satir[ListeTablosu.id]
        return ListeOzeti(
            id = id.toString(),
            sahip = satir.kullaniciOzeti() ?: return null,
            baslik = satir[ListeTablosu.baslik],
            gorunurluk = ListeGorunurlugu.valueOf(satir[ListeTablosu.gorunurluk]),
            oyunSayisi = oyunIdleri.size,
            onIzleme = oyunIdleri.take(ON_IZLEME).mapNotNull(ozetler::get),
            begeniSayisi = begeniler[id] ?: 0,
        )
    }

    private class GecerliIstek(val baslik: String, val aciklama: String?, val gorunurluk: ListeGorunurlugu, val oyunlar: List<UUID>)

    private suspend fun dogrula(istek: ListeIstegi): GecerliIstek? {
        val baslik = istek.baslik.trim()
        val aciklama = istek.aciklama?.trim()?.takeIf { it.isNotEmpty() }
        if (baslik.isEmpty() || baslik.length > ListeKurallari.BASLIK_EN_FAZLA) return null
        if (aciklama != null && aciklama.length > ListeKurallari.ACIKLAMA_EN_FAZLA) return null
        if (istek.oyunIdleri.size > ListeKurallari.OYUN_EN_FAZLA || istek.oyunIdleri.toSet().size != istek.oyunIdleri.size) return null
        val oyunlar = istek.oyunIdleri.map { ham -> runCatching { UUID.fromString(ham) }.getOrNull() ?: return null }
        if (!oyunlar.all { katalog.varMi(it) }) return null
        return GecerliIstek(baslik, aciklama, istek.gorunurluk, oyunlar)
    }

    private fun JdbcTransaction.oyunlariYaz(id: UUID, oyunlar: List<UUID>) {
        oyunlar.forEachIndexed { i, oyun ->
            ListeOyunuTablosu.insert {
                it[listeId] = id
                it[oyunId] = oyun
                it[sira] = i.toShort()
            }
        }
    }

    private fun simdi(): OffsetDateTime = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)

    private companion object {
        const val SAYFA = 30
        const val ON_IZLEME = 4
    }
}

private fun OffsetDateTime.anlik(): Instant = toInstant().let { Instant.fromEpochSeconds(it.epochSecond, it.nano) }

internal object ListeTablosu : Table("liste") {
    val id = javaUUID("id")
    val kullaniciId = javaUUID("kullanici_id")
    val baslik = text("baslik")
    val aciklama = text("aciklama").nullable()
    val gorunurluk = text("gorunurluk")
    val olusturulma = timestampWithTimeZone("olusturulma")
    val guncellenme = timestampWithTimeZone("guncellenme")
    override val primaryKey = PrimaryKey(id)
}

internal object ListeOyunuTablosu : Table("liste_oyunu") {
    val listeId = javaUUID("liste_id")
    val oyunId = javaUUID("oyun_id")
    val sira = short("sira")
    override val primaryKey = PrimaryKey(listeId, oyunId)
}

internal object ListeBegenisiTablosu : Table("liste_begenisi") {
    val listeId = javaUUID("liste_id")
    val kullaniciId = javaUUID("kullanici_id")
    val olusturulma = timestampWithTimeZone("olusturulma")
    override val primaryKey = PrimaryKey(listeId, kullaniciId)
}
