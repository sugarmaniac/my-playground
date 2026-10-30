package com.sosyaloyun.server

import com.sosyaloyun.shared.ArkadaslikDurumu
import com.sosyaloyun.shared.BildirimTuru
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.KullaniciListesi
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Kullanıcılar arasındaki ilişkiler ve bunlardan çıkan görme yetkisi (CONTEXT.md: Takip, Görünürlük). */
class SosyalGraf(private val veritabani: Database, private val saat: Clock) {
    /** Yeni takip, takip edilene bildirim düşürür. */
    suspend fun takipEt(eden: UUID, edilen: UUID) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                if (takipEtIcinde(eden, edilen, saat)) with(BildirimOlaylari) { bildir(edilen, BildirimTuru.YENI_TAKIPCI, eden, simdi()) }
            }
        }
    }

    /** Arkadaşlık sürerken takipten çıkılamaz; bu durumda false döner. */
    suspend fun birak(eden: UUID, edilen: UUID): Boolean = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            if (arkadasMiIcinde(eden, edilen)) return@transaction false
            TakipTablosu.deleteWhere { (takipEden eq eden) and (takipEdilen eq edilen) }
            with(BildirimOlaylari) { bildirimiSil(edilen, BildirimTuru.YENI_TAKIPCI, eden) }
            true
        }
    }

    /** İstek gönderir; karşı taraf zaten istek göndermişse doğrudan arkadaş olunur. */
    suspend fun arkadaslikIste(gonderen: UUID, alan: UUID) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                when {
                    arkadasMiIcinde(gonderen, alan) -> Unit
                    istekVarMi(alan, gonderen) -> {
                        arkadasYap(alan, gonderen)
                        with(BildirimOlaylari) { bildir(alan, BildirimTuru.ARKADASLIK_KABULU, gonderen, simdi()) }
                    }
                    else -> {
                        val eklendi = ArkadaslikIstegiTablosu.insertIgnore {
                            it[ArkadaslikIstegiTablosu.gonderen] = gonderen
                            it[ArkadaslikIstegiTablosu.alan] = alan
                            it[olusturulma] = simdi()
                        }.insertedCount
                        if (eklendi == 1) with(BildirimOlaylari) { bildir(alan, BildirimTuru.ARKADASLIK_ISTEGI, gonderen, simdi()) }
                    }
                }
            }
        }
    }

    /** [alan]'a [gonderen]'den gelen isteği kabul eder; iki yönlü takip oluşur. İstek yoksa false. */
    suspend fun kabulEt(alan: UUID, gonderen: UUID): Boolean = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            if (!istekVarMi(gonderen, alan)) return@transaction false
            arkadasYap(gonderen, alan)
            with(BildirimOlaylari) { bildir(gonderen, BildirimTuru.ARKADASLIK_KABULU, alan, simdi()) }
            true
        }
    }

    suspend fun reddet(alan: UUID, gonderen: UUID): Boolean = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val silindi = ArkadaslikIstegiTablosu.deleteWhere { (ArkadaslikIstegiTablosu.gonderen eq gonderen) and (ArkadaslikIstegiTablosu.alan eq alan) } > 0
            if (silindi) with(BildirimOlaylari) { bildirimiSil(alan, BildirimTuru.ARKADASLIK_ISTEGI, gonderen) }
            silindi
        }
    }

    /** Arkadaşlığı bitirir ya da gönderilmiş isteği geri alır; takipler olduğu gibi kalır. */
    suspend fun arkadasligiBitir(kullanici: UUID, diger: UUID) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val (a, b) = sirali(kullanici, diger)
                val silindi = ArkadaslikTablosu.deleteWhere { (kullaniciA eq a) and (kullaniciB eq b) }
                if (silindi > 0) with(AkisOlaylari) { arkadaslikBitti(a, b) }
                if (ArkadaslikIstegiTablosu.deleteWhere { (gonderen eq kullanici) and (alan eq diger) } > 0) {
                    with(BildirimOlaylari) { bildirimiSil(diger, BildirimTuru.ARKADASLIK_ISTEGI, kullanici) }
                }
            }
        }
    }

    suspend fun arkadaslikDurumu(izleyen: UUID, diger: UUID): ArkadaslikDurumu = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            when {
                arkadasMiIcinde(izleyen, diger) -> ArkadaslikDurumu.ARKADAS
                istekVarMi(izleyen, diger) -> ArkadaslikDurumu.ISTEK_GONDERILDI
                istekVarMi(diger, izleyen) -> ArkadaslikDurumu.ISTEK_ALINDI
                else -> ArkadaslikDurumu.YOK
            }
        }
    }

    suspend fun arkadasMi(a: UUID, b: UUID): Boolean = withContext(Dispatchers.IO) { transaction(veritabani) { arkadasMiIcinde(a, b) } }

    suspend fun arkadaslar(kullaniciId: UUID, izleyen: UUID, imlec: String?): KullaniciListesi = withContext(Dispatchers.IO) {
        val baslangic = Imlec.baslangic(imlec)
        transaction(veritabani) {
            val gizli = engellileri(izleyen)
            val digerleri = ArkadaslikTablosu.selectAll()
                .where {
                    ((ArkadaslikTablosu.kullaniciA eq kullaniciId) and (ArkadaslikTablosu.kullaniciB notInList gizli)) or
                        ((ArkadaslikTablosu.kullaniciB eq kullaniciId) and (ArkadaslikTablosu.kullaniciA notInList gizli))
                }
                .orderBy(ArkadaslikTablosu.olusturulma to SortOrder.DESC)
                .limit(SAYFA + 1)
                .offset(baslangic.toLong())
                .map { if (it[ArkadaslikTablosu.kullaniciA] == kullaniciId) it[ArkadaslikTablosu.kullaniciB] else it[ArkadaslikTablosu.kullaniciA] }
            KullaniciListesi(ozetler(digerleri.take(SAYFA)), Imlec.sonraki(digerleri.size, baslangic, SAYFA))
        }
    }

    suspend fun gelenIstekler(kullaniciId: UUID, imlec: String?): KullaniciListesi = withContext(Dispatchers.IO) {
        val baslangic = Imlec.baslangic(imlec)
        transaction(veritabani) {
            val gonderenler = ArkadaslikIstegiTablosu.selectAll()
                .where { ArkadaslikIstegiTablosu.alan eq kullaniciId }
                .orderBy(ArkadaslikIstegiTablosu.olusturulma to SortOrder.DESC)
                .limit(SAYFA + 1)
                .offset(baslangic.toLong())
                .map { it[ArkadaslikIstegiTablosu.gonderen] }
            KullaniciListesi(ozetler(gonderenler.take(SAYFA)), Imlec.sonraki(gonderenler.size, baslangic, SAYFA))
        }
    }

    private fun JdbcTransaction.ozetler(idler: List<UUID>): List<com.sosyaloyun.shared.KullaniciOzeti> {
        if (idler.isEmpty()) return emptyList()
        val satirlar = KullaniciTablosu.selectAll().where { KullaniciTablosu.id inList idler }.associateBy { it[KullaniciTablosu.id] }
        return idler.mapNotNull { satirlar[it]?.kullaniciOzeti() }
    }

    private fun JdbcTransaction.istekVarMi(gonderen: UUID, alan: UUID): Boolean =
        ArkadaslikIstegiTablosu.selectAll()
            .where { (ArkadaslikIstegiTablosu.gonderen eq gonderen) and (ArkadaslikIstegiTablosu.alan eq alan) }
            .any()

    private fun JdbcTransaction.arkadasYap(gonderen: UUID, alan: UUID) {
        ArkadaslikIstegiTablosu.deleteWhere {
            ((ArkadaslikIstegiTablosu.gonderen eq gonderen) and (ArkadaslikIstegiTablosu.alan eq alan)) or
                ((ArkadaslikIstegiTablosu.gonderen eq alan) and (ArkadaslikIstegiTablosu.alan eq gonderen))
        }
        val (a, b) = sirali(gonderen, alan)
        val eklendi = ArkadaslikTablosu.insertIgnore {
            it[kullaniciA] = a
            it[kullaniciB] = b
            it[olusturulma] = simdi()
        }.insertedCount
        if (eklendi == 1) with(AkisOlaylari) { arkadasOldu(gonderen, alan, simdi()) }
        // Arkadaşlık takibi kapsar (CONTEXT.md: Arkadaşlık).
        takipEtIcinde(gonderen, alan, saat)
        takipEtIcinde(alan, gonderen, saat)
    }

    private fun simdi() = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)

    /**
     * Engeller: iki yöndeki takip, arkadaşlık ve arkadaşlık istekleri ile birbirlerinin günlük kayıtlarına verdikleri
     * beğeniler silinir. Engellenene bildirim ya da akış olayı gitmez.
     */
    suspend fun engelle(kullanici: UUID, hedef: UUID) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                EngellemeTablosu.insertIgnore {
                    it[engelleyen] = kullanici
                    it[engellenen] = hedef
                    it[olusturulma] = simdi()
                }
                TakipTablosu.deleteWhere { ((takipEden eq kullanici) and (takipEdilen eq hedef)) or ((takipEden eq hedef) and (takipEdilen eq kullanici)) }
                val (a, b) = sirali(kullanici, hedef)
                if (ArkadaslikTablosu.deleteWhere { (kullaniciA eq a) and (kullaniciB eq b) } > 0) with(AkisOlaylari) { arkadaslikBitti(a, b) }
                ArkadaslikIstegiTablosu.deleteWhere { ((gonderen eq kullanici) and (alan eq hedef)) or ((gonderen eq hedef) and (alan eq kullanici)) }
                begenileriSil(begenen = hedef, sahip = kullanici)
                begenileriSil(begenen = kullanici, sahip = hedef)
                with(BildirimOlaylari) { aradakiBildirimleriSil(kullanici, hedef) }
            }
        }
    }

    /** Engeli kaldırır; silinen ilişkiler geri gelmez. */
    suspend fun engeliKaldir(kullanici: UUID, hedef: UUID) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) { EngellemeTablosu.deleteWhere { (engelleyen eq kullanici) and (engellenen eq hedef) } }
        }
    }

    /** Takip ve arkadaşlık bozulmadan [hedef]'in aktivitesi [kullanici]'nın akışından kalkar. */
    suspend fun sessizeAl(kullanici: UUID, hedef: UUID) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                SessizeAlmaTablosu.insertIgnore {
                    it[alan] = kullanici
                    it[alinan] = hedef
                    it[olusturulma] = simdi()
                }
            }
        }
    }

    suspend fun sessizdenCikar(kullanici: UUID, hedef: UUID) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) { SessizeAlmaTablosu.deleteWhere { (alan eq kullanici) and (alinan eq hedef) } }
        }
    }

    suspend fun sessizeAldiMi(kullanici: UUID, hedef: UUID): Boolean = withContext(Dispatchers.IO) {
        transaction(veritabani) { SessizeAlmaTablosu.selectAll().where { (SessizeAlmaTablosu.alan eq kullanici) and (SessizeAlmaTablosu.alinan eq hedef) }.any() }
    }

    /** İki kullanıcıdan biri diğerini engellemişse true; ikisi de birbirini hiçbir yerde göremez. */
    suspend fun engelliMi(a: UUID, b: UUID): Boolean = withContext(Dispatchers.IO) {
        transaction(veritabani) { b in engellileri(a) }
    }

    suspend fun engellediklerim(kullaniciId: UUID, imlec: String?): KullaniciListesi =
        iliskiListesi(EngellemeTablosu.engelleyen, EngellemeTablosu.engellenen, EngellemeTablosu.olusturulma, kullaniciId, imlec)

    suspend fun sessizeAldiklarim(kullaniciId: UUID, imlec: String?): KullaniciListesi =
        iliskiListesi(SessizeAlmaTablosu.alan, SessizeAlmaTablosu.alinan, SessizeAlmaTablosu.olusturulma, kullaniciId, imlec)

    private suspend fun iliskiListesi(
        sahipSutunu: Column<UUID>,
        kisiSutunu: Column<UUID>,
        zamanSutunu: Column<java.time.OffsetDateTime>,
        kullaniciId: UUID,
        imlec: String?,
    ): KullaniciListesi = withContext(Dispatchers.IO) {
        val baslangic = Imlec.baslangic(imlec)
        transaction(veritabani) {
            val satirlar = sahipSutunu.table.join(KullaniciTablosu, JoinType.INNER, kisiSutunu, KullaniciTablosu.id)
                .selectAll()
                .where { sahipSutunu eq kullaniciId }
                .orderBy(zamanSutunu to SortOrder.DESC, kisiSutunu to SortOrder.ASC)
                .limit(SAYFA + 1)
                .offset(baslangic.toLong())
                .mapNotNull { it.kullaniciOzeti() }
            KullaniciListesi(satirlar.take(SAYFA), Imlec.sonraki(satirlar.size, baslangic, SAYFA))
        }
    }

    private fun JdbcTransaction.begenileriSil(begenen: UUID, sahip: UUID) {
        val kayitlari = GunlukKaydiTablosu.select(GunlukKaydiTablosu.id).where { GunlukKaydiTablosu.kullaniciId eq sahip }
        BegeniTablosu.deleteWhere { (kullaniciId eq begenen) and (gunlukKaydiId inSubQuery kayitlari) }
    }

    suspend fun takipEdiyorMu(eden: UUID, edilen: UUID): Boolean = withContext(Dispatchers.IO) {
        transaction(veritabani) { takipEdiyorMuIcinde(eden, edilen) }
    }

    data class Sayaclar(val takipci: Int, val takipEdilen: Int, val arkadas: Int)

    suspend fun sayaclar(kullaniciId: UUID): Sayaclar = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            Sayaclar(
                takipci = TakipTablosu.selectAll().where { TakipTablosu.takipEdilen eq kullaniciId }.count().toInt(),
                takipEdilen = TakipTablosu.selectAll().where { TakipTablosu.takipEden eq kullaniciId }.count().toInt(),
                arkadas = ArkadaslikTablosu.selectAll()
                    .where { (ArkadaslikTablosu.kullaniciA eq kullaniciId) or (ArkadaslikTablosu.kullaniciB eq kullaniciId) }
                    .count().toInt(),
            )
        }
    }

    /** Başkasının listelerinde [izleyen]'le arasında engel olan kişiler görünmez. */
    suspend fun takipciler(kullaniciId: UUID, izleyen: UUID, imlec: String?) = liste(TakipTablosu.takipEdilen, TakipTablosu.takipEden, kullaniciId, izleyen, imlec)

    suspend fun takipEdilenler(kullaniciId: UUID, izleyen: UUID, imlec: String?) = liste(TakipTablosu.takipEden, TakipTablosu.takipEdilen, kullaniciId, izleyen, imlec)

    /**
     * [izleyen]'in [sahip]'in günlük kayıtlarından görebileceği görünürlük seviyeleri: sahibi hepsini,
     * takipçisi Herkes ve Takipçiler'i, arkadaşı ayrıca Arkadaşlar'ı, diğerleri yalnızca Herkes'i.
     */
    suspend fun gorebilecegi(izleyen: UUID, sahip: UUID): Set<Gorunurluk> = withContext(Dispatchers.IO) {
        if (izleyen == sahip) return@withContext Gorunurluk.entries.toSet()
        transaction(veritabani) {
            if (sahip in engellileri(izleyen)) return@transaction emptySet()
            buildSet {
                add(Gorunurluk.HERKES)
                if (takipEdiyorMuIcinde(izleyen, sahip)) add(Gorunurluk.TAKIPCILER)
                if (arkadasMiIcinde(izleyen, sahip)) add(Gorunurluk.ARKADASLAR)
            }
        }
    }

    private suspend fun liste(kosulSutunu: Column<UUID>, kisiSutunu: Column<UUID>, kullaniciId: UUID, izleyen: UUID, imlec: String?): KullaniciListesi =
        withContext(Dispatchers.IO) {
            val baslangic = Imlec.baslangic(imlec)
            transaction(veritabani) {
                val satirlar = TakipTablosu.join(KullaniciTablosu, JoinType.INNER, kisiSutunu, KullaniciTablosu.id)
                    .selectAll()
                    .where { (kosulSutunu eq kullaniciId) and (kisiSutunu notInList engellileri(izleyen)) }
                    .orderBy(TakipTablosu.olusturulma to SortOrder.DESC, kisiSutunu to SortOrder.ASC)
                    .limit(SAYFA + 1)
                    .offset(baslangic.toLong())
                    .mapNotNull { it.kullaniciOzeti() }
                KullaniciListesi(satirlar.take(SAYFA), Imlec.sonraki(satirlar.size, baslangic, SAYFA))
            }
        }

    companion object {
        const val SAYFA = 30
    }
}

/** Yeni takip oluştuysa true. Bildirimi çağıran yazar: arkadaşlık ve davet koduyla gelen takipler bildirim düşürmez. */
internal fun JdbcTransaction.takipEtIcinde(eden: UUID, edilen: UUID, saat: Clock): Boolean {
    if (eden == edilen) return false
    return TakipTablosu.insertIgnore {
        it[takipEden] = eden
        it[takipEdilen] = edilen
        it[olusturulma] = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)
    }.insertedCount == 1
}

internal fun JdbcTransaction.takipEdiyorMuIcinde(eden: UUID, edilen: UUID): Boolean =
    TakipTablosu.selectAll().where { (TakipTablosu.takipEden eq eden) and (TakipTablosu.takipEdilen eq edilen) }.any()

internal fun JdbcTransaction.arkadasMiIcinde(a: UUID, b: UUID): Boolean {
    val (kucuk, buyuk) = sirali(a, b)
    return ArkadaslikTablosu.selectAll().where { (ArkadaslikTablosu.kullaniciA eq kucuk) and (ArkadaslikTablosu.kullaniciB eq buyuk) }.any()
}

/** [kullanici]'nın engellediği ve onu engelleyen kişiler; iki taraf birbirini hiçbir yerde göremez. */
internal fun JdbcTransaction.engellileri(kullanici: UUID): List<UUID> =
    EngellemeTablosu.selectAll()
        .where { (EngellemeTablosu.engelleyen eq kullanici) or (EngellemeTablosu.engellenen eq kullanici) }
        .map { if (it[EngellemeTablosu.engelleyen] == kullanici) it[EngellemeTablosu.engellenen] else it[EngellemeTablosu.engelleyen] }

/** [kullanici]'nın sessize aldığı kişiler. */
internal fun JdbcTransaction.sessizeAldiklari(kullanici: UUID): List<UUID> =
    SessizeAlmaTablosu.select(SessizeAlmaTablosu.alinan).where { SessizeAlmaTablosu.alan eq kullanici }.map { it[SessizeAlmaTablosu.alinan] }

/** PostgreSQL UUID'leri bayt sırasıyla karşılaştırır; küçük harf onaltılık metin karşılaştırması bununla aynıdır. */
private fun sirali(a: UUID, b: UUID): Pair<UUID, UUID> = if (a.toString() < b.toString()) a to b else b to a

private object ArkadaslikIstegiTablosu : Table("arkadaslik_istegi") {
    val gonderen = javaUUID("gonderen")
    val alan = javaUUID("alan")
    val olusturulma = timestampWithTimeZone("olusturulma")
    override val primaryKey = PrimaryKey(gonderen, alan)
}

internal object ArkadaslikTablosu : Table("arkadaslik") {
    val kullaniciA = javaUUID("kullanici_a")
    val kullaniciB = javaUUID("kullanici_b")
    val olusturulma = timestampWithTimeZone("olusturulma")
    override val primaryKey = PrimaryKey(kullaniciA, kullaniciB)
}

internal object TakipTablosu : Table("takip") {
    val takipEden = javaUUID("takip_eden")
    val takipEdilen = javaUUID("takip_edilen")
    val olusturulma = timestampWithTimeZone("olusturulma")
    override val primaryKey = PrimaryKey(takipEden, takipEdilen)
}

internal object EngellemeTablosu : Table("engelleme") {
    val engelleyen = javaUUID("engelleyen")
    val engellenen = javaUUID("engellenen")
    val olusturulma = timestampWithTimeZone("olusturulma")
    override val primaryKey = PrimaryKey(engelleyen, engellenen)
}

internal object SessizeAlmaTablosu : Table("sessize_alma") {
    val alan = javaUUID("alan")
    val alinan = javaUUID("alinan")
    val olusturulma = timestampWithTimeZone("olusturulma")
    override val primaryKey = PrimaryKey(alan, alinan)
}
