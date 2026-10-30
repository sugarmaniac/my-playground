package com.sosyaloyun.server

import com.sosyaloyun.shared.Durum
import com.sosyaloyun.shared.GunlukOgesi
import com.sosyaloyun.shared.GunlukSayfasi
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.KutuphaneOgesi
import com.sosyaloyun.shared.KutuphaneSayfasi
import com.sosyaloyun.shared.OrtakOyunlar
import com.sosyaloyun.shared.ProfilSayfasi
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Profil sayfası ve sekmeleri; sekmeler imleçle sayfalanır (imleç: atlanan öğe sayısı). */
class Profiller(
    private val veritabani: Database,
    private val katalog: Katalog,
    private val kullanicilar: Kullanicilar,
    private val sosyalGraf: SosyalGraf,
    private val begeniler: Begeniler,
    private val saat: Clock,
) {
    /** [izleyen]'in gördüğü haliyle [kullaniciId]'nin profili; profili yoksa null. */
    suspend fun sayfa(kullaniciId: UUID, izleyen: UUID = kullaniciId): ProfilSayfasi? {
        val profil = kullanicilar.ben(kullaniciId).profil ?: return null
        val (biyografi, buYil, vitrinIdleri) = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val biyografi = KullaniciTablosu.select(KullaniciTablosu.biyografi).where { KullaniciTablosu.id eq kullaniciId }.single()[KullaniciTablosu.biyografi]
                val vitrin = VitrinTablosu.selectAll()
                    .where { VitrinTablosu.kullaniciId eq kullaniciId }
                    .orderBy(VitrinTablosu.sira to SortOrder.ASC)
                    .map { it[VitrinTablosu.oyunId] }
                Triple(biyografi, buYilBitirilen(kullaniciId), vitrin)
            }
        }
        val ozetler = katalog.ozetleri(vitrinIdleri)
        val sayaclar = sosyalGraf.sayaclar(kullaniciId)
        val kendisi = izleyen == kullaniciId
        val gizli = sosyalGraf.gizliMi(kullaniciId)
        // Gizli profilin takipçisi olmayan başlığı görür (ad, biyografi, sayaçlar) ama içeriği görmez (#48).
        val icerikGizli = !kendisi && gizli && !sosyalGraf.takipEdiyorMu(izleyen, kullaniciId)
        return ProfilSayfasi(
            kullanici = profil,
            biyografi = biyografi,
            buYilBitirilen = if (icerikGizli) 0 else buYil,
            vitrin = if (icerikGizli) emptyList() else vitrinIdleri.mapNotNull(ozetler::get),
            takipciSayisi = sayaclar.takipci,
            takipEdilenSayisi = sayaclar.takipEdilen,
            takipEdiyorum = if (izleyen == kullaniciId) null else sosyalGraf.takipEdiyorMu(izleyen, kullaniciId),
            arkadasSayisi = sayaclar.arkadas,
            arkadaslik = if (izleyen == kullaniciId) null else sosyalGraf.arkadaslikDurumu(izleyen, kullaniciId),
            sessizeAldim = if (izleyen == kullaniciId) null else sosyalGraf.sessizeAldiMi(izleyen, kullaniciId),
            gizli = gizli,
            takipIstegiGonderdim = if (kendisi || !gizli) null else sosyalGraf.takipIstegiGonderdiMi(izleyen, kullaniciId),
            icerikGizli = icerikGizli,
            kurator = sosyalGraf.kuratorMu(kullaniciId),
        )
    }

    /** Bu takvim yılında bitirilen farklı oyunlar: durumu bu yıl Bitirdim olan ya da bitirme tarihi bu yıl olan bir günlük kaydı. */
    private fun buYilBitirilen(kullaniciId: UUID): Int {
        val bugun = saat.now().toJavaInstant().atZone(TURKIYE).toLocalDate()
        val yilBasi = LocalDate.of(bugun.year, 1, 1)
        val sonrakiYil = yilBasi.plusYears(1)
        val durumla = KutuphaneGirisiTablosu.select(KutuphaneGirisiTablosu.oyunId)
            .where {
                (KutuphaneGirisiTablosu.kullaniciId eq kullaniciId) and (KutuphaneGirisiTablosu.durum eq Durum.BITIRDIM.name) and
                    (KutuphaneGirisiTablosu.durumDegisme greaterEq yilBasi.atStartOfDay(TURKIYE).toOffsetDateTime()) and
                    (KutuphaneGirisiTablosu.durumDegisme less sonrakiYil.atStartOfDay(TURKIYE).toOffsetDateTime())
            }
            .map { it[KutuphaneGirisiTablosu.oyunId] }
        val gunlukle = GunlukKaydiTablosu.select(GunlukKaydiTablosu.oyunId)
            .where {
                (GunlukKaydiTablosu.kullaniciId eq kullaniciId) and
                    (GunlukKaydiTablosu.bitis greaterEq yilBasi) and (GunlukKaydiTablosu.bitis less sonrakiYil)
            }
            .map { it[GunlukKaydiTablosu.oyunId] }
        return (durumla + gunlukle).toSet().size
    }

    /** Vitrin en fazla dört farklı, katalogda olan oyun; liste baştan yazılır. Geçersizse false. */
    suspend fun vitriniKaydet(kullaniciId: UUID, oyunIdleri: List<String>): Boolean {
        val idler = oyunIdleri.map { runCatching { UUID.fromString(it) }.getOrNull() ?: return false }
        if (idler.size > com.sosyaloyun.shared.ProfilKurallari.VITRIN_EN_FAZLA || idler.toSet().size != idler.size) return false
        if (!idler.all { katalog.varMi(it) }) return false
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                VitrinTablosu.deleteWhere { VitrinTablosu.kullaniciId eq kullaniciId }
                idler.forEachIndexed { i, oyunId ->
                    VitrinTablosu.insert {
                        it[VitrinTablosu.kullaniciId] = kullaniciId
                        it[sira] = (i + 1).toShort()
                        it[VitrinTablosu.oyunId] = oyunId
                    }
                }
            }
        }
        return true
    }

    /** Günlük sekmesi (tarih sırasıyla) ya da yalnızca incelemeli kayıtlar; [izleyen]'in görme yetkisi olanlar. */
    suspend fun gunluk(kullaniciId: UUID, izleyen: UUID, yalnizcaIncelemeler: Boolean, imlec: String?): GunlukSayfasi {
        val baslangic = baslangic(imlec)
        val gorunurlukler = sosyalGraf.gorebilecegi(izleyen, kullaniciId).map { it.name }
        val kayitlar = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                var kosul: Op<Boolean> = (GunlukKaydiTablosu.kullaniciId eq kullaniciId) and (GunlukKaydiTablosu.gorunurluk inList gorunurlukler)
                if (yalnizcaIncelemeler) kosul = kosul and GunlukKaydiTablosu.inceleme.isNotNull()
                GunlukKaydiTablosu.selectAll()
                    .where(kosul)
                    .orderBy(
                        GunlukKaydiTablosu.tarih to SortOrder.DESC,
                        GunlukKaydiTablosu.olusturulma to SortOrder.DESC,
                        GunlukKaydiTablosu.id to SortOrder.DESC,
                    )
                    .limit(SAYFA + 1)
                    .offset(baslangic.toLong())
                    .map { it.gunlukKaydi() }
            }
        }
        val sayfa = begeniler.ekle(izleyen, kayitlar.take(SAYFA))
        val oyunlar = katalog.ozetleri(sayfa.map { UUID.fromString(it.oyunId) })
        return GunlukSayfasi(
            ogeler = sayfa.map { GunlukOgesi(it, oyunlar.getValue(UUID.fromString(it.oyunId))) },
            sonrakiImlec = sonraki(kayitlar.size, baslangic),
        )
    }

    /**
     * Oyunlar sekmesi: yalnızca istek listesinde olan girişler hariç bütün kütüphane girişleri; [durum]
     * bir Durum ya da "durumsuz". İstek listesi sekmesi: istek listesindeki girişler, durumdan bağımsız.
     */
    suspend fun kutuphane(kullaniciId: UUID, istekListesi: Boolean, durum: String?, imlec: String?): KutuphaneSayfasi {
        val baslangic = baslangic(imlec)
        val satirlar = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val t = KutuphaneGirisiTablosu
                var kosul: Op<Boolean> = t.kullaniciId eq kullaniciId
                kosul = if (istekListesi) {
                    kosul and (t.istekListesinde eq true)
                } else {
                    kosul and oynanmis()
                }
                when (durum) {
                    null -> Unit
                    DURUMSUZ -> kosul = kosul and t.durum.isNull()
                    else -> kosul = kosul and (t.durum eq (Durum.entries.firstOrNull { it.name.equals(durum, ignoreCase = true) }?.name ?: "-"))
                }
                t.selectAll()
                    .where(kosul)
                    .orderBy(t.guncellenme to SortOrder.DESC, t.oyunId to SortOrder.ASC)
                    .limit(SAYFA + 1)
                    .offset(baslangic.toLong())
                    .map {
                        it[t.oyunId] to KutuphaneGirisi(
                            durum = it[t.durum]?.let(Durum::valueOf),
                            puan = it[t.puan]?.toInt(),
                            begendim = it[t.begendim],
                            istekListesinde = it[t.istekListesinde],
                            yuzdeYuz = it[t.yuzdeYuz],
                        )
                    }
            }
        }
        val sayfa = satirlar.take(SAYFA)
        val oyunlar = katalog.ozetleri(sayfa.map { it.first })
        return KutuphaneSayfasi(
            ogeler = sayfa.map { (oyunId, giris) -> KutuphaneOgesi(oyunlar.getValue(oyunId), giris) },
            sonrakiImlec = sonraki(satirlar.size, baslangic),
        )
    }

    /** Arkadaşın profilinde: ikisinin de oynadığı oyunlar ve sahibinin bitirip izleyenin istek listesinde olanlar. */
    suspend fun ortakOyunlar(sahip: UUID, izleyen: UUID): OrtakOyunlar {
        val (ortak, eslesen) = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val t = KutuphaneGirisiTablosu
                fun oyunlari(kosul: Op<Boolean>) = t.select(t.oyunId).where(kosul).map { it[t.oyunId] }.toSet()
                val sahibinOynadiklari = oyunlari((t.kullaniciId eq sahip) and oynanmis())
                val izleyeninOynadiklari = oyunlari((t.kullaniciId eq izleyen) and oynanmis())
                val sahibinBitirdikleri = oyunlari((t.kullaniciId eq sahip) and (t.durum eq Durum.BITIRDIM.name))
                val izleyeninIstedikleri = oyunlari((t.kullaniciId eq izleyen) and (t.istekListesinde eq true))
                (sahibinOynadiklari intersect izleyeninOynadiklari) to (sahibinBitirdikleri intersect izleyeninIstedikleri)
            }
        }
        val ozetler = katalog.ozetleri((ortak + eslesen).toList())
        fun sirala(idler: Set<UUID>) = idler.mapNotNull(ozetler::get).sortedBy { it.ad }
        return OrtakOyunlar(sirala(ortak), sirala(eslesen))
    }

    private fun baslangic(imlec: String?) = Imlec.baslangic(imlec)

    private fun sonraki(gelen: Int, baslangic: Int) = Imlec.sonraki(gelen, baslangic, SAYFA)

    companion object {
        const val SAYFA = 20
        const val DURUMSUZ = "durumsuz"
    }
}

/** Oynandığı bilinen kütüphane girişi: yalnızca istek listesinde olmayan (durum, puan, beğeni, Steam süresi ya da günlük kaydı var). */
private fun oynanmis(): Op<Boolean> {
    val t = KutuphaneGirisiTablosu
    val gunlukVar = exists(
        GunlukKaydiTablosu.select(GunlukKaydiTablosu.id)
            .where { (GunlukKaydiTablosu.kullaniciId eq t.kullaniciId) and (GunlukKaydiTablosu.oyunId eq t.oyunId) },
    )
    return t.durum.isNotNull() or t.puan.isNotNull() or (t.begendim eq true) or t.steamSureDakika.isNotNull() or t.konsolAgi.isNotNull() or gunlukVar
}

private object VitrinTablosu : Table("vitrin_oyunu") {
    val kullaniciId = javaUUID("kullanici_id")
    val sira = short("sira")
    val oyunId = javaUUID("oyun_id")
    override val primaryKey = PrimaryKey(kullaniciId, sira)
}
