package com.sosyaloyun.server

import com.sosyaloyun.shared.AkisOgesi
import com.sosyaloyun.shared.AkisOlayTuru
import com.sosyaloyun.shared.AkisSayfasi
import com.sosyaloyun.shared.AkisSekmesi
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.KullaniciListesi
import java.time.Instant as JavaInstant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Akış: takip edilenlerin akışa düşen olayları, yeniden eskiye. Beta ölçeğinde okuma anında sorgulanır;
 * yazma anında dağıtıma geçilirse bu sınıfın arayüzü değişmez (spec #1).
 */
class Akis(private val veritabani: Database, private val katalog: Katalog, private val begeniler: Begeniler) {
    suspend fun sayfa(izleyen: UUID, sekme: AkisSekmesi, imlec: String?): AkisSayfasi {
        val (satirlar, kisiler, istekListem) = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                var kosul = gorunenOlaylar(izleyen, sekme)
                imlecCoz(imlec)?.let { (zaman, id) ->
                    kosul = kosul and ((AkisOlayiTablosu.olusturulma less zaman) or ((AkisOlayiTablosu.olusturulma eq zaman) and (AkisOlayiTablosu.id less id)))
                }
                val satirlar = AkisOlayiTablosu.join(GunlukKaydiTablosu, JoinType.LEFT, AkisOlayiTablosu.gunlukKaydiId, GunlukKaydiTablosu.id)
                    .selectAll()
                    .where(kosul)
                    .orderBy(AkisOlayiTablosu.olusturulma to SortOrder.DESC, AkisOlayiTablosu.id to SortOrder.DESC)
                    .limit(SAYFA + 1)
                    .map { satir ->
                        Satir(
                            id = satir[AkisOlayiTablosu.id],
                            tur = AkisOlayTuru.valueOf(satir[AkisOlayiTablosu.tur]),
                            kisi = satir[AkisOlayiTablosu.kullaniciId],
                            oyun = satir[AkisOlayiTablosu.oyunId],
                            diger = satir[AkisOlayiTablosu.digerKullaniciId],
                            zaman = satir[AkisOlayiTablosu.olusturulma],
                            gunluk = if (satir.getOrNull(GunlukKaydiTablosu.id) != null) satir.gunlukKaydi() else null,
                        )
                    }
                val kisiIdleri = satirlar.flatMap { listOfNotNull(it.kisi, it.diger) }.distinct()
                val kisiler = if (kisiIdleri.isEmpty()) emptyMap() else
                    KullaniciTablosu.selectAll().where { KullaniciTablosu.id inList kisiIdleri }
                        .mapNotNull { satir -> satir.kullaniciOzeti()?.let { satir[KullaniciTablosu.id] to it } }.toMap()
                val oyunIdleri = satirlar.mapNotNull { it.oyun }.distinct()
                val istekListem = if (oyunIdleri.isEmpty()) emptySet() else
                    KutuphaneGirisiTablosu.select(KutuphaneGirisiTablosu.oyunId)
                        .where {
                            (KutuphaneGirisiTablosu.kullaniciId eq izleyen) and (KutuphaneGirisiTablosu.oyunId inList oyunIdleri) and
                                (KutuphaneGirisiTablosu.istekListesinde eq true)
                        }
                        .map { it[KutuphaneGirisiTablosu.oyunId] }.toSet()
                Triple(satirlar, kisiler, istekListem)
            }
        }
        val oyunlar = katalog.ozetleri(satirlar.mapNotNull { it.oyun })
        val kayitlar = begeniler.ekle(izleyen, satirlar.mapNotNull { it.gunluk }).associateBy { it.id }
        val ogeler = satirlar.take(SAYFA).mapNotNull { satir ->
            AkisOgesi(
                id = satir.id.toString(),
                tur = satir.tur,
                kisi = kisiler[satir.kisi] ?: return@mapNotNull null,
                zaman = satir.zaman.toInstant().let { Instant.fromEpochSeconds(it.epochSecond, it.nano) },
                oyun = satir.oyun?.let(oyunlar::get),
                gunlukKaydi = satir.gunluk?.let { kayitlar[it.id] },
                arkadas = satir.diger?.let(kisiler::get),
                istekListemde = satir.oyun in istekListem,
            )
        }
        val sayfa = satirlar.take(SAYFA)
        return AkisSayfasi(
            ogeler = ogeler,
            sonrakiImlec = if (satirlar.size > SAYFA) sayfa.last().imlec() else null,
            enYeniImlec = if (imlec == null) sayfa.firstOrNull()?.imlec() else null,
        )
    }

    /** [sonra] imlecinden daha yeni, izleyenin görebileceği olay sayısı (üstteki "yeni gönderiler" uyarısı). */
    suspend fun yeniSayisi(izleyen: UUID, sekme: AkisSekmesi, sonra: String?): Int = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            var kosul = gorunenOlaylar(izleyen, sekme)
            imlecCoz(sonra)?.let { (zaman, id) ->
                kosul = kosul and ((AkisOlayiTablosu.olusturulma greater zaman) or ((AkisOlayiTablosu.olusturulma eq zaman) and (AkisOlayiTablosu.id greater id)))
            }
            AkisOlayiTablosu.join(GunlukKaydiTablosu, JoinType.LEFT, AkisOlayiTablosu.gunlukKaydiId, GunlukKaydiTablosu.id)
                .selectAll().where(kosul).limit(YENI_EN_FAZLA).count().toInt()
        }
    }

    /** Boş akış için takip önerileri: izleyenin takip etmediği, en çok takipçisi olan kullanıcılar. */
    suspend fun oneriler(izleyen: UUID): KullaniciListesi = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val takipEttikleri = TakipTablosu.select(TakipTablosu.takipEdilen).where { TakipTablosu.takipEden eq izleyen }
                .map { it[TakipTablosu.takipEdilen] } + izleyen
            val sayi = Count(TakipTablosu.takipEden)
            val takipciSayilari = TakipTablosu.select(TakipTablosu.takipEdilen, sayi).groupBy(TakipTablosu.takipEdilen)
                .associate { it[TakipTablosu.takipEdilen] to it[sayi] }
            val adaylar = KullaniciTablosu.selectAll()
                .where { KullaniciTablosu.kullaniciAdi.isNotNull() and (KullaniciTablosu.id notInList takipEttikleri) }
                .mapNotNull { satir -> satir.kullaniciOzeti()?.let { satir[KullaniciTablosu.id] to it } }
            KullaniciListesi(
                kullanicilar = adaylar
                    .sortedWith(compareByDescending<Pair<UUID, com.sosyaloyun.shared.KullaniciOzeti>> { takipciSayilari[it.first] ?: 0L }.thenBy { it.second.kullaniciAdi })
                    .take(ONERI_SAYISI)
                    .map { it.second },
                sonrakiImlec = null,
            )
        }
    }

    /**
     * İzleyenin akışında görebileceği olaylar: takip ettiklerinin (Arkadaşlar sekmesinde arkadaşlarının) olayları,
     * izleyenin kendisinin dahil olmadığı. Günlük kayıtlarında sessizler düşmez; görünürlük uygulanır.
     */
    private fun JdbcTransaction.gorunenOlaylar(izleyen: UUID, sekme: AkisSekmesi): Op<Boolean> {
        val arkadaslar = ArkadaslikTablosu.selectAll()
            .where { (ArkadaslikTablosu.kullaniciA eq izleyen) or (ArkadaslikTablosu.kullaniciB eq izleyen) }
            .map { if (it[ArkadaslikTablosu.kullaniciA] == izleyen) it[ArkadaslikTablosu.kullaniciB] else it[ArkadaslikTablosu.kullaniciA] }
        val aktorler = when (sekme) {
            AkisSekmesi.TAKIP -> TakipTablosu.select(TakipTablosu.takipEdilen).where { TakipTablosu.takipEden eq izleyen }.map { it[TakipTablosu.takipEdilen] }
            AkisSekmesi.ARKADASLAR -> arkadaslar
        }
        val o = AkisOlayiTablosu
        val g = GunlukKaydiTablosu
        val kimin = (o.kullaniciId inList aktorler) or ((o.tur eq AkisOlayTuru.ARKADAS_OLDU.name) and (o.digerKullaniciId inList aktorler))
        val kendisiDegil = (o.kullaniciId neq izleyen) and (o.digerKullaniciId.isNull() or (o.digerKullaniciId neq izleyen))
        val gorunur = (o.tur neq AkisOlayTuru.GUNLUK_KAYDI.name) or (
            (g.sessiz eq false) and (
                (g.gorunurluk inList listOf(Gorunurluk.HERKES.name, Gorunurluk.TAKIPCILER.name)) or
                    ((g.gorunurluk eq Gorunurluk.ARKADASLAR.name) and (o.kullaniciId inList arkadaslar))
                )
            )
        return kimin and kendisiDegil and gorunur
    }

    private data class Satir(
        val id: UUID,
        val tur: AkisOlayTuru,
        val kisi: UUID,
        val oyun: UUID?,
        val diger: UUID?,
        val zaman: OffsetDateTime,
        val gunluk: com.sosyaloyun.shared.GunlukKaydi?,
    ) {
        /** Zaman (mikrosaniye) ve kimlik: aynı anda girilmiş olaylarda da sıra kaymaz. */
        fun imlec(): String = "${zaman.toInstant().let { it.epochSecond * 1_000_000 + it.nano / 1000 }}_$id"
    }

    private fun imlecCoz(imlec: String?): Pair<OffsetDateTime, UUID>? {
        val (mikro, id) = imlec?.split('_', limit = 2)?.takeIf { it.size == 2 } ?: return null
        val zaman = mikro.toLongOrNull() ?: return null
        val kimlik = runCatching { UUID.fromString(id) }.getOrNull() ?: return null
        return JavaInstant.ofEpochSecond(zaman / 1_000_000, (zaman % 1_000_000) * 1000).atOffset(ZoneOffset.UTC) to kimlik
    }

    companion object {
        const val SAYFA = 20
        const val ONERI_SAYISI = 10
        const val YENI_EN_FAZLA = 99
    }
}

/** Olaylar, kaynaklarını yazan işlemin içinde yazılır. */
internal object AkisOlaylari {
    fun JdbcTransaction.gunlukKaydiGirildi(kullaniciId: UUID, oyunId: UUID, gunlukKaydiId: UUID, zaman: OffsetDateTime) =
        yaz(AkisOlayTuru.GUNLUK_KAYDI, kullaniciId, zaman, oyunId = oyunId, gunlukKaydiId = gunlukKaydiId)

    fun JdbcTransaction.oynamayaBasladi(kullaniciId: UUID, oyunId: UUID, zaman: OffsetDateTime) =
        yaz(AkisOlayTuru.OYNAMAYA_BASLADI, kullaniciId, zaman, oyunId = oyunId)

    fun JdbcTransaction.arkadasOldu(a: UUID, b: UUID, zaman: OffsetDateTime) = yaz(AkisOlayTuru.ARKADAS_OLDU, a, zaman, diger = b)

    /** Arkadaşlık bitince "arkadaş oldular" olayı akıştan kalkar. */
    fun JdbcTransaction.arkadaslikBitti(a: UUID, b: UUID) {
        AkisOlayiTablosu.deleteWhere {
            (tur eq AkisOlayTuru.ARKADAS_OLDU.name) and (
                ((kullaniciId eq a) and (digerKullaniciId eq b)) or ((kullaniciId eq b) and (digerKullaniciId eq a))
                )
        }
    }

    private fun JdbcTransaction.yaz(tur: AkisOlayTuru, kullaniciId: UUID, zaman: OffsetDateTime, oyunId: UUID? = null, gunlukKaydiId: UUID? = null, diger: UUID? = null) {
        AkisOlayiTablosu.insert {
            it[id] = UUID.randomUUID()
            it[AkisOlayiTablosu.tur] = tur.name
            it[AkisOlayiTablosu.kullaniciId] = kullaniciId
            it[AkisOlayiTablosu.oyunId] = oyunId
            it[AkisOlayiTablosu.gunlukKaydiId] = gunlukKaydiId
            it[digerKullaniciId] = diger
            it[olusturulma] = zaman
        }
    }
}

private object AkisOlayiTablosu : Table("akis_olayi") {
    val id = javaUUID("id")
    val tur = text("tur")
    val kullaniciId = javaUUID("kullanici_id")
    val oyunId = javaUUID("oyun_id").nullable()
    val gunlukKaydiId = javaUUID("gunluk_kaydi_id").nullable()
    val digerKullaniciId = javaUUID("diger_kullanici_id").nullable()
    val olusturulma = timestampWithTimeZone("olusturulma")
    override val primaryKey = PrimaryKey(id)
}
