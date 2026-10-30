package com.sosyaloyun.server

import com.sosyaloyun.shared.AnaOyunOzeti
import com.sosyaloyun.shared.AramaKurallari
import com.sosyaloyun.shared.Magaza
import com.sosyaloyun.shared.MagazaLinki
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.OyunDetayi
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.OyunTuru
import com.sosyaloyun.shared.Tur
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.CharLength
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.anyFrom
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.stringLiteral
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert

/** Oyun kataloğu: kaynaktan gelen kayıtları kaydedilebilir oyunlara çevirir ve aramayı yapar. */
class Katalog(private val veritabani: Database) {
    /**
     * Bir kaynak kaydını kataloğa uygular. Port ve sürümler ana oyunlarında birleşir; ana oyunları
     * henüz yoksa `false` döner ve kayıt daha sonra yeniden denenmelidir.
     */
    suspend fun uygula(oyun: KaynakOyun): Boolean = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val birlesecegiOyun = when {
                oyun.surumAnasiKaynakId != null -> oyun.surumAnasiKaynakId
                oyun.tur in BIRLESEN_TURLER -> oyun.anaOyunKaynakId
                else -> null
            }
            when {
                birlesecegiOyun != null -> birlestir(oyun, birlesecegiOyun)
                oyun.tur in KAYDEDILEBILIR_TURLER -> {
                    kaydet(oyun)
                    true
                }
                else -> {
                    kaldirIcinde(oyun.kaynakId)
                    true
                }
            }
        }
    }

    /** Kaynakta silinen kayıt: oyunun kendi kaydıysa oyun gizlenir, birleşmiş bir port/sürümse yalnızca o kayıt düşer. */
    suspend fun kaldir(kaynakId: Long) {
        withContext(Dispatchers.IO) { transaction(veritabani) { kaldirIcinde(kaynakId) } }
    }

    /** Popülerlik değerleri oyunun kendi kaydının kimliğiyle gelir. */
    suspend fun populerlikleriYaz(degerler: List<KaynakPopulerlik>) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                degerler.forEach { deger ->
                    val oyunId = kaynakKaydi(deger.oyunKaynakId)?.takeIf { it.birincil }?.oyunId ?: return@forEach
                    OyunTablosu.update({ OyunTablosu.id eq oyunId }) { it[populerlik] = deger.deger }
                }
            }
        }
    }

    /** Bütün kelimeleri (sırası önemsiz) adında geçen oyunlar; popüler ve kısa adlılar önce. */
    suspend fun ara(sorgu: String, imlec: String?): OyunAramaYaniti = withContext(Dispatchers.IO) {
        val kelimeler = AramaMetni.kelimeler(sorgu)
        if (kelimeler.joinToString("").length < AramaKurallari.EN_AZ_KARAKTER) return@withContext OyunAramaYaniti(emptyList(), null)
        val baslangic = Imlec.baslangic(imlec)
        transaction(veritabani) {
            val kosul = kelimeler.fold<String, Op<Boolean>>(OyunTablosu.kaldirildi eq false) { op, kelime ->
                op and (OyunTablosu.aramaMetni like "%$kelime%")
            }
            val satirlar = OyunTablosu.selectAll()
                .where(kosul)
                .orderBy(
                    OyunTablosu.populerlik to SortOrder.DESC,
                    CharLength(OyunTablosu.ad) to SortOrder.ASC,
                    OyunTablosu.ad to SortOrder.ASC,
                    OyunTablosu.id to SortOrder.ASC,
                )
                .limit(SAYFA + 1)
                .offset(baslangic.toLong())
                .toList()
            val sayfa = satirlar.take(SAYFA)
            val oyunlar = ozetler(sayfa.map { it[OyunTablosu.id] })
            OyunAramaYaniti(
                oyunlar = sayfa.map { oyunlar.getValue(it[OyunTablosu.id]) },
                sonrakiImlec = Imlec.sonraki(satirlar.size, baslangic, SAYFA),
            )
        }
    }

    /** Steam uygulama kimliklerinin eşlendiği, kaynakta silinmemiş oyunlar; eşlenmeyenler haritada yok. */
    suspend fun steamEslesmeleri(uygulamaIdleri: Collection<Long>): Map<Long, UUID> = withContext(Dispatchers.IO) {
        if (uygulamaIdleri.isEmpty()) return@withContext emptyMap()
        transaction(veritabani) {
            OyunKaynagiTablosu.join(OyunTablosu, org.jetbrains.exposed.v1.core.JoinType.INNER, OyunKaynagiTablosu.oyunId, OyunTablosu.id)
                .selectAll()
                .where { (OyunKaynagiTablosu.kaynak eq STEAM) and (OyunKaynagiTablosu.kaynakId inList uygulamaIdleri) and (OyunTablosu.kaldirildi eq false) }
                .associate { it[OyunKaynagiTablosu.kaynakId] to it[OyunKaynagiTablosu.oyunId] }
        }
    }

    /** Bir türdeki ya da bir platformdaki ana oyunlar, popüler önce; ikisi de verilmezse bütün ana oyunlar. */
    suspend fun listele(tur: Tur?, platform: String?, imlec: String?): OyunAramaYaniti = withContext(Dispatchers.IO) {
        val baslangic = Imlec.baslangic(imlec)
        transaction(veritabani) {
            var kosul: Op<Boolean> = (OyunTablosu.kaldirildi eq false) and (OyunTablosu.tur eq OyunTuru.ANA_OYUN.name)
            tur?.let { kosul = kosul and (stringLiteral(it.igdbAdi) eq anyFrom(OyunTablosu.turler)) }
            platform?.let { ad ->
                val platformdakiler = OyunKaynagiTablosu.select(OyunKaynagiTablosu.oyunId)
                    .where { (OyunKaynagiTablosu.kaynak eq IGDB) and (stringLiteral(ad) eq anyFrom(OyunKaynagiTablosu.platformlar)) }
                kosul = kosul and (OyunTablosu.id inSubQuery platformdakiler)
            }
            val idler = OyunTablosu.select(OyunTablosu.id)
                .where(kosul)
                .orderBy(OyunTablosu.populerlik to SortOrder.DESC, OyunTablosu.ad to SortOrder.ASC, OyunTablosu.id to SortOrder.ASC)
                .limit(SAYFA + 1)
                .offset(baslangic.toLong())
                .map { it[OyunTablosu.id] }
            val sayfa = idler.take(SAYFA)
            val oyunlar = ozetler(sayfa)
            OyunAramaYaniti(sayfa.mapNotNull(oyunlar::get), Imlec.sonraki(idler.size, baslangic, SAYFA))
        }
    }

    /** En popüler ana oyunlar; Steam bağlamayan kullanıcı onboarding'de bunlardan işaretler. */
    suspend fun populerler(adet: Int): List<OyunOzeti> = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val idler = OyunTablosu.select(OyunTablosu.id)
                .where { (OyunTablosu.kaldirildi eq false) and (OyunTablosu.tur eq OyunTuru.ANA_OYUN.name) }
                .orderBy(OyunTablosu.populerlik to SortOrder.DESC, OyunTablosu.id to SortOrder.ASC)
                .limit(adet)
                .map { it[OyunTablosu.id] }
            val oyunlar = ozetler(idler)
            idler.mapNotNull(oyunlar::get)
        }
    }

    /** Oyun özetleri (kaynakta silinmiş oyunlar dahil; kullanıcının geçmişi onları gösterir). */
    suspend fun ozetleri(idler: List<UUID>): Map<UUID, OyunOzeti> = withContext(Dispatchers.IO) {
        transaction(veritabani) { ozetler(idler.distinct()) }
    }

    /** Oyunların türleri (IGDB adlarıyla); istatistikler için (#55). */
    suspend fun turleri(idler: Collection<UUID>): Map<UUID, List<String>> = withContext(Dispatchers.IO) {
        if (idler.isEmpty()) return@withContext emptyMap()
        transaction(veritabani) {
            OyunTablosu.select(OyunTablosu.id, OyunTablosu.turler).where { OyunTablosu.id inList idler.distinct() }
                .associate { it[OyunTablosu.id] to it[OyunTablosu.turler] }
        }
    }

    /** Katalogda olan (kaynakta silinmemiş) bir oyun mu. */
    suspend fun varMi(oyunId: UUID): Boolean = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            OyunTablosu.selectAll().where { (OyunTablosu.id eq oyunId) and (OyunTablosu.kaldirildi eq false) }.any()
        }
    }

    /** Kaynakta silinmiş ya da hiç olmayan oyun için null. */
    suspend fun detay(oyunId: UUID): OyunDetayi? = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val satir = OyunTablosu.selectAll()
                .where { (OyunTablosu.id eq oyunId) and (OyunTablosu.kaldirildi eq false) }
                .singleOrNull() ?: return@transaction null
            val kaynaklar = OyunKaynagiTablosu.selectAll().where { OyunKaynagiTablosu.oyunId eq oyunId }.toList()
            val igdbKimlikleri = kaynaklar.filter { it[OyunKaynagiTablosu.kaynak] == IGDB }.map { it[OyunKaynagiTablosu.kaynakId] }
            val eklentiIdleri = OyunTablosu.selectAll()
                .where { (OyunTablosu.anaOyunKaynakId inList igdbKimlikleri) and (OyunTablosu.kaldirildi eq false) }
                .orderBy(OyunTablosu.cikisTarihi to SortOrder.ASC_NULLS_LAST, OyunTablosu.ad to SortOrder.ASC)
                .map { it[OyunTablosu.id] }
            val ozetler = ozetler(listOf(oyunId) + eklentiIdleri)
            OyunDetayi(
                oyun = ozetler.getValue(oyunId),
                aciklama = satir[OyunTablosu.ozet],
                gelistiriciler = satir[OyunTablosu.gelistiriciler],
                yayincilar = satir[OyunTablosu.yayincilar],
                turler = satir[OyunTablosu.turler],
                magazaLinkleri = magazaLinkleri(oyunId, kaynaklar),
                eklentiler = eklentiIdleri.map(ozetler::getValue),
                kaynakUrl = satir[OyunTablosu.slug]?.let { "https://www.igdb.com/games/$it" } ?: "https://www.igdb.com",
            )
        }
    }

    /** Kaynağın linkleri; Steam linki yoksa eşlemedeki Steam uygulamasından türetilir. Mağaza sırasıyla. */
    private fun JdbcTransaction.magazaLinkleri(oyunId: UUID, kaynaklar: List<org.jetbrains.exposed.v1.core.ResultRow>): List<MagazaLinki> {
        val linkler = MagazaLinkiTablosu.selectAll()
            .where { MagazaLinkiTablosu.oyunId eq oyunId }
            .associate { Magaza.valueOf(it[MagazaLinkiTablosu.magaza]) to it[MagazaLinkiTablosu.url] }
            .toMutableMap()
        if (Magaza.STEAM !in linkler) {
            kaynaklar.filter { it[OyunKaynagiTablosu.kaynak] == STEAM }
                .minOfOrNull { it[OyunKaynagiTablosu.kaynakId] }
                ?.let { linkler[Magaza.STEAM] = "https://store.steampowered.com/app/$it" }
        }
        return linkler.entries.sortedBy { it.key.ordinal }.map { MagazaLinki(it.key, it.value) }
    }

    /** Oyun özetleri: platformlar bütün IGDB kayıtlarının birleşimi, DLC/genişlemelerde ana oyun. */
    private fun JdbcTransaction.ozetler(idler: List<UUID>): Map<UUID, OyunOzeti> {
        if (idler.isEmpty()) return emptyMap()
        val platformlar = OyunKaynagiTablosu.selectAll()
            .where { (OyunKaynagiTablosu.kaynak eq IGDB) and (OyunKaynagiTablosu.oyunId inList idler) }
            .orderBy(OyunKaynagiTablosu.birincil to SortOrder.DESC, OyunKaynagiTablosu.kaynakId to SortOrder.ASC)
            .groupBy({ it[OyunKaynagiTablosu.oyunId] }, { it[OyunKaynagiTablosu.platformlar] })
            .mapValues { (_, listeler) -> listeler.flatten().distinct() }
        val satirlar = OyunTablosu.selectAll().where { OyunTablosu.id inList idler }.toList()
        val anaKaynakIdleri = satirlar.mapNotNull { it[OyunTablosu.anaOyunKaynakId] }
        val anaOyunlar = if (anaKaynakIdleri.isEmpty()) emptyMap() else
            OyunKaynagiTablosu.join(OyunTablosu, org.jetbrains.exposed.v1.core.JoinType.INNER, OyunKaynagiTablosu.oyunId, OyunTablosu.id)
                .selectAll()
                .where {
                    (OyunKaynagiTablosu.kaynak eq IGDB) and (OyunKaynagiTablosu.kaynakId inList anaKaynakIdleri) and
                        (OyunTablosu.kaldirildi eq false)
                }
                .associate { it[OyunKaynagiTablosu.kaynakId] to AnaOyunOzeti(it[OyunTablosu.id].toString(), it[OyunTablosu.ad]) }
        return satirlar.associate { satir ->
            val id = satir[OyunTablosu.id]
            id to OyunOzeti(
                id = id.toString(),
                ad = satir[OyunTablosu.ad],
                tur = OyunTuru.valueOf(satir[OyunTablosu.tur]),
                cikisYili = satir[OyunTablosu.cikisTarihi]?.year,
                kapakUrl = satir[OyunTablosu.kapakKimligi]?.let(::kapakUrl),
                platformlar = platformlar[id].orEmpty(),
                anaOyun = satir[OyunTablosu.anaOyunKaynakId]?.let(anaOyunlar::get),
            )
        }
    }

    private fun JdbcTransaction.kaydet(oyun: KaynakOyun) {
        val onceki = kaynakKaydi(oyun.kaynakId)
        val oyunId = onceki?.takeIf { it.birincil }?.oyunId ?: UUID.randomUUID()
        val tur = turu(oyun.tur)
        OyunTablosu.upsert {
            it[id] = oyunId
            it[ad] = oyun.ad
            it[aramaMetni] = AramaMetni.duzenle(oyun.ad)
            it[OyunTablosu.tur] = tur.name
            it[anaOyunKaynakId] = if (tur == OyunTuru.DLC || tur == OyunTuru.GENISLEME) oyun.anaOyunKaynakId else null
            it[cikisTarihi] = oyun.cikisTarihi
            it[kapakKimligi] = oyun.kapakKimligi
            it[ozet] = oyun.ozet
            it[slug] = oyun.slug
            it[gelistiriciler] = oyun.gelistiriciler
            it[yayincilar] = oyun.yayincilar
            it[turler] = oyun.turler
            it[kaldirildi] = false
        }
        MagazaLinkiTablosu.deleteWhere { MagazaLinkiTablosu.oyunId eq oyunId }
        oyun.magazaLinkleri.distinctBy { it.magaza }.forEach { link ->
            MagazaLinkiTablosu.insert {
                it[MagazaLinkiTablosu.oyunId] = oyunId
                it[magaza] = link.magaza.name
                it[url] = link.url
            }
        }
        kaynakKaydiYaz(oyun, oyunId, birincil = true)
    }

    private fun JdbcTransaction.birlestir(oyun: KaynakOyun, hedefKaynakId: Long): Boolean {
        val hedefOyunId = kaynakKaydi(hedefKaynakId)?.oyunId ?: return false
        val onceki = kaynakKaydi(oyun.kaynakId)
        // Daha önce ayrı oyun sayılmış bir kayıt artık bir sürüm: eski oyun gizlenir.
        if (onceki != null && onceki.birincil && onceki.oyunId != hedefOyunId) gizle(onceki.oyunId)
        kaynakKaydiYaz(oyun, hedefOyunId, birincil = false)
        return true
    }

    private fun JdbcTransaction.kaynakKaydiYaz(oyun: KaynakOyun, oyunId: UUID, birincil: Boolean) {
        OyunKaynagiTablosu.upsert {
            it[kaynak] = IGDB
            it[kaynakId] = oyun.kaynakId
            it[OyunKaynagiTablosu.oyunId] = oyunId
            it[OyunKaynagiTablosu.birincil] = birincil
            it[platformlar] = oyun.platformlar
        }
        oyun.steamUygulamaIdleri.forEach { steamId ->
            OyunKaynagiTablosu.upsert {
                it[kaynak] = STEAM
                it[kaynakId] = steamId
                it[OyunKaynagiTablosu.oyunId] = oyunId
                it[OyunKaynagiTablosu.birincil] = false
                it[platformlar] = emptyList()
            }
        }
    }

    private fun JdbcTransaction.kaldirIcinde(kaynakId: Long) {
        val kayit = kaynakKaydi(kaynakId) ?: return
        if (kayit.birincil) {
            gizle(kayit.oyunId)
        } else {
            OyunKaynagiTablosu.deleteWhere { (kaynak eq IGDB) and (OyunKaynagiTablosu.kaynakId eq kaynakId) }
        }
    }

    private fun JdbcTransaction.gizle(oyunId: UUID) {
        OyunTablosu.update({ OyunTablosu.id eq oyunId }) { it[kaldirildi] = true }
    }

    private data class KaynakKaydi(val oyunId: UUID, val birincil: Boolean)

    private fun JdbcTransaction.kaynakKaydi(kaynakId: Long): KaynakKaydi? =
        OyunKaynagiTablosu.selectAll()
            .where { (OyunKaynagiTablosu.kaynak eq IGDB) and (OyunKaynagiTablosu.kaynakId eq kaynakId) }
            .singleOrNull()
            ?.let { KaynakKaydi(it[OyunKaynagiTablosu.oyunId], it[OyunKaynagiTablosu.birincil]) }

    private fun turu(tur: KaynakTuru): OyunTuru = when (tur) {
        KaynakTuru.ANA_OYUN -> OyunTuru.ANA_OYUN
        KaynakTuru.DLC -> OyunTuru.DLC
        KaynakTuru.GENISLEME, KaynakTuru.BAGIMSIZ_GENISLEME -> OyunTuru.GENISLEME
        KaynakTuru.REMAKE -> OyunTuru.REMAKE
        KaynakTuru.REMASTER -> OyunTuru.REMASTER
        else -> error("kaydedilemeyen tür: $tur")
    }

    companion object {
        const val SAYFA = 20
        private const val IGDB = "igdb"
        private const val STEAM = "steam"

        private val KAYDEDILEBILIR_TURLER = setOf(
            KaynakTuru.ANA_OYUN, KaynakTuru.DLC, KaynakTuru.GENISLEME, KaynakTuru.BAGIMSIZ_GENISLEME,
            KaynakTuru.REMAKE, KaynakTuru.REMASTER,
        )
        private val BIRLESEN_TURLER = setOf(KaynakTuru.PORT, KaynakTuru.GENISLETILMIS_OYUN)

        /** Kapaklar IGDB'nin görsel sunucusundan; API çağrısı değildir. */
        fun kapakUrl(kapakKimligi: String) = "https://images.igdb.com/igdb/image/upload/t_cover_big/$kapakKimligi.jpg"
    }
}

/** Kataloğu kaynaktan sayfa sayfa içeri alır (ADR 0001); webhook'lar arada güncel tutar. */
class KatalogIceAktarici(private val kaynak: KatalogKaynagi, private val katalog: Katalog) {
    suspend fun tamIceAktar() {
        val bekleyenler = mutableListOf<KaynakOyun>()
        var son = 0L
        while (true) {
            val sayfa = kaynak.oyunSayfasi(son, SAYFA)
            if (sayfa.isEmpty()) break
            sayfa.forEach { if (!katalog.uygula(it)) bekleyenler += it }
            son = sayfa.last().kaynakId
        }
        // Ana oyunundan önce gelen port ve sürümler.
        bekleyenler.forEach { katalog.uygula(it) }
    }

    suspend fun populerlikleriGuncelle() {
        var son = 0L
        while (true) {
            val sayfa = kaynak.populerlikSayfasi(son, SAYFA)
            if (sayfa.isEmpty()) break
            katalog.populerlikleriYaz(sayfa)
            son = sayfa.last().oyunKaynakId
        }
    }

    /** Webhook'tan gelen oluşturma/güncelleme: kayıt kaynaktan yeniden okunur; kaynakta yoksa kaldırılır. */
    suspend fun guncelle(kaynakId: Long) {
        val oyun = kaynak.oyunlar(listOf(kaynakId)).singleOrNull()
        if (oyun == null) katalog.kaldir(kaynakId) else katalog.uygula(oyun)
    }

    suspend fun kaldir(kaynakId: Long) = katalog.kaldir(kaynakId)

    private companion object {
        const val SAYFA = 500
    }
}

private object OyunTablosu : Table("oyun") {
    val id = javaUUID("id")
    val ad = text("ad")
    val aramaMetni = text("arama_metni")
    val tur = text("tur")
    val anaOyunKaynakId = long("ana_oyun_kaynak_id").nullable()
    val cikisTarihi = date("cikis_tarihi").nullable()
    val kapakKimligi = text("kapak_kimligi").nullable()
    val ozet = text("ozet").nullable()
    val populerlik = double("populerlik")
    val kaldirildi = bool("kaldirildi")
    val slug = text("slug").nullable()
    val gelistiriciler = array<String>("gelistiriciler")
    val yayincilar = array<String>("yayincilar")
    val turler = array<String>("turler")
    override val primaryKey = PrimaryKey(id)
}

private object MagazaLinkiTablosu : Table("oyun_magaza_linki") {
    val oyunId = javaUUID("oyun_id")
    val magaza = text("magaza")
    val url = text("url")
    override val primaryKey = PrimaryKey(oyunId, magaza)
}

private object OyunKaynagiTablosu : Table("oyun_kaynagi") {
    val kaynak = text("kaynak")
    val kaynakId = long("kaynak_id")
    val oyunId = javaUUID("oyun_id")
    val birincil = bool("birincil")
    val platformlar = array<String>("platformlar")
    override val primaryKey = PrimaryKey(kaynak, kaynakId)
}
