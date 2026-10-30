package com.sosyaloyun.server

import com.sosyaloyun.shared.AramaKurallari
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.BenYaniti
import com.sosyaloyun.shared.HesapSilme
import com.sosyaloyun.shared.KimlikSaglayici
import com.sosyaloyun.shared.KullaniciAdiDurumu
import com.sosyaloyun.shared.KullaniciListesi
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.ProfilIstegi
import com.sosyaloyun.shared.ProfilKurallari
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class Kullanicilar(private val veritabani: Database) {
    data class Sonuc(val kullaniciId: UUID, val yeni: Boolean)

    enum class ProfilSonucu { KAYDEDILDI, DAVET_GEREKLI, KULLANICI_ADI_GECERSIZ, GORUNEN_AD_GECERSIZ, BIYOGRAFI_COK_UZUN, KULLANICI_ADI_ALINMIS }

    /** Kimliğin sahibi olan kullanıcıyı döner; ilk girişse kullanıcıyı oluşturur. */
    suspend fun bulVeyaOlustur(saglayici: KimlikSaglayici, haricKimlik: String): Sonuc = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            mevcutKullanici(saglayici, haricKimlik)?.let { return@transaction Sonuc(it, yeni = false) }

            val yeniId = UUID.randomUUID()
            KullaniciTablosu.insert { it[id] = yeniId }
            val eklendi = KimlikTablosu.insertIgnore {
                it[KimlikTablosu.saglayici] = saglayici.name
                it[KimlikTablosu.haricKimlik] = haricKimlik
                it[kullaniciId] = yeniId
            }.insertedCount
            if (eklendi == 1) return@transaction Sonuc(yeniId, yeni = true)

            // Aynı kimlikle eşzamanlı bir ilk giriş (çift dokunma, yeniden deneme) önce davrandı.
            KullaniciTablosu.deleteWhere { id eq yeniId }
            Sonuc(mevcutKullanici(saglayici, haricKimlik)!!, yeni = false)
        }
    }

    suspend fun ben(kullaniciId: UUID): BenYaniti = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val satir = KullaniciTablosu.selectAll().where { KullaniciTablosu.id eq kullaniciId }.single()
            BenYaniti(
                kullaniciId = kullaniciId.toString(),
                davetKabulEdildi = satir[KullaniciTablosu.davetKabulZamani] != null,
                profil = satir.kullaniciOzeti(),
                onboardingTamamlandi = satir[KullaniciTablosu.onboardingTamamlandi],
            )
        }
    }

    /**
     * Hesabı anında ve kalıcı olarak siler (ADR 0008). Kullanıcıya bağlı her tablo `ON DELETE CASCADE` ile birlikte
     * silinir; topluluk puanı her istekte hesaplandığından silinenin puanı kendiliğinden düşer. Onay tutmazsa false.
     */
    suspend fun sil(kullaniciId: UUID, onay: String): Boolean = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val satir = KullaniciTablosu.selectAll().where { KullaniciTablosu.id eq kullaniciId }.forUpdate().single()
            if (!HesapSilme.onaylandiMi(onay, satir[KullaniciTablosu.kullaniciAdi])) return@transaction false
            KullaniciTablosu.deleteWhere { id eq kullaniciId }
            true
        }
    }

    suspend fun onboardingiTamamla(kullaniciId: UUID) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                KullaniciTablosu.update({ KullaniciTablosu.id eq kullaniciId }) { it[onboardingTamamlandi] = true }
            }
        }
    }

    suspend fun davetKabulEdildiMi(kullaniciId: UUID): Boolean = ben(kullaniciId).davetKabulEdildi

    suspend fun kullaniciAdiDurumu(hamAd: String, isteyen: UUID): Pair<String, KullaniciAdiDurumu> = withContext(Dispatchers.IO) {
        val ad = ProfilKurallari.kullaniciAdiniDuzenle(hamAd)
        if (!ProfilKurallari.kullaniciAdiGecerliMi(ad)) return@withContext ad to KullaniciAdiDurumu.GECERSIZ
        val alinmis = transaction(veritabani) {
            KullaniciTablosu.selectAll()
                .where { (KullaniciTablosu.kullaniciAdi eq ad) and (KullaniciTablosu.id neq isteyen) }
                .any()
        }
        ad to if (alinmis) KullaniciAdiDurumu.ALINMIS else KullaniciAdiDurumu.MUSAIT
    }

    suspend fun profilKaydet(kullaniciId: UUID, istek: ProfilIstegi): ProfilSonucu = withContext(Dispatchers.IO) {
        if (!davetKabulEdildiMi(kullaniciId)) return@withContext ProfilSonucu.DAVET_GEREKLI
        val ad = ProfilKurallari.kullaniciAdiniDuzenle(istek.kullaniciAdi)
        if (!ProfilKurallari.kullaniciAdiGecerliMi(ad)) return@withContext ProfilSonucu.KULLANICI_ADI_GECERSIZ
        if (!ProfilKurallari.gorunenAdGecerliMi(istek.gorunenAd)) return@withContext ProfilSonucu.GORUNEN_AD_GECERSIZ
        val biyografi = istek.biyografi?.trim()?.takeIf { it.isNotEmpty() }
        if ((biyografi?.length ?: 0) > ProfilKurallari.BIYOGRAFI_EN_FAZLA) return@withContext ProfilSonucu.BIYOGRAFI_COK_UZUN
        try {
            transaction(veritabani) {
                KullaniciTablosu.update({ KullaniciTablosu.id eq kullaniciId }) {
                    it[kullaniciAdi] = ad
                    it[gorunenAd] = istek.gorunenAd.trim()
                    it[avatar] = istek.avatar.name
                    it[KullaniciTablosu.biyografi] = biyografi
                    it[aramaMetni] = AramaMetni.duzenle("$ad ${istek.gorunenAd}")
                }
            }
            ProfilSonucu.KAYDEDILDI
        } catch (e: ExposedSQLException) {
            if (e.sqlState == BENZERSIZLIK_IHLALI) ProfilSonucu.KULLANICI_ADI_ALINMIS else throw e
        }
    }

    /** Onboarding'i bitirmiş (kullanıcı adı seçmiş) kullanıcının kimliği. */
    suspend fun kullaniciAdindan(hamAd: String): UUID? = withContext(Dispatchers.IO) {
        val ad = ProfilKurallari.kullaniciAdiniDuzenle(hamAd)
        transaction(veritabani) {
            KullaniciTablosu.selectAll().where { KullaniciTablosu.kullaniciAdi eq ad }.singleOrNull()?.get(KullaniciTablosu.id)
        }
    }

    /** Kullanıcı adında ya da görünen adında bütün kelimeleri geçen, profili olan kullanıcılar. */
    /** [izleyen]'le arasında engel olanlar sonuçlarda görünmez. */
    suspend fun ara(sorgu: String, izleyen: UUID, imlec: String?): KullaniciListesi = withContext(Dispatchers.IO) {
        val kelimeler = AramaMetni.kelimeler(sorgu)
        if (kelimeler.joinToString("").length < AramaKurallari.EN_AZ_KARAKTER) return@withContext KullaniciListesi(emptyList(), null)
        val baslangic = Imlec.baslangic(imlec)
        transaction(veritabani) {
            val gorunur = KullaniciTablosu.kullaniciAdi.isNotNull() and (KullaniciTablosu.id notInList engellileri(izleyen))
            val kosul = kelimeler.fold<String, org.jetbrains.exposed.v1.core.Op<Boolean>>(gorunur) { op, kelime ->
                op and (KullaniciTablosu.aramaMetni like "%$kelime%")
            }
            val satirlar = KullaniciTablosu.selectAll()
                .where(kosul)
                .orderBy(KullaniciTablosu.kullaniciAdi to SortOrder.ASC)
                .limit(SAYFA + 1)
                .offset(baslangic.toLong())
                .mapNotNull { it.kullaniciOzeti() }
            KullaniciListesi(satirlar.take(SAYFA), Imlec.sonraki(satirlar.size, baslangic, SAYFA))
        }
    }

    private fun mevcutKullanici(saglayici: KimlikSaglayici, haricKimlik: String): UUID? =
        KimlikTablosu.selectAll()
            .where { (KimlikTablosu.saglayici eq saglayici.name) and (KimlikTablosu.haricKimlik eq haricKimlik) }
            .singleOrNull()
            ?.get(KimlikTablosu.kullaniciId)

    private companion object {
        const val BENZERSIZLIK_IHLALI = "23505"
        const val SAYFA = 30
    }
}

/** Kullanıcı adı seçilmemişse null. */
internal fun ResultRow.kullaniciOzeti(): KullaniciOzeti? {
    val ad = this[KullaniciTablosu.kullaniciAdi] ?: return null
    return KullaniciOzeti(
        kullaniciId = this[KullaniciTablosu.id].toString(),
        kullaniciAdi = ad,
        gorunenAd = this[KullaniciTablosu.gorunenAd]!!,
        avatar = Avatar.valueOf(this[KullaniciTablosu.avatar]!!),
    )
}

internal object KullaniciTablosu : Table("kullanici") {
    val id = javaUUID("id")
    val davetKabulZamani = timestampWithTimeZone("davet_kabul_zamani").nullable()
    val kullaniciAdi = text("kullanici_adi").nullable()
    val gorunenAd = text("gorunen_ad").nullable()
    val avatar = text("avatar").nullable()
    val sonPlatform = text("son_platform").nullable()
    val biyografi = text("biyografi").nullable()
    val aramaMetni = text("arama_metni").nullable()
    val onboardingTamamlandi = bool("onboarding_tamamlandi").default(false)

    /** Kullanıcının kendi numarasının cihazda üretilmiş hash'i (ADR 0003). */
    val telefonHash = text("telefon_hash").nullable()

    /** Moderasyon yaptırımları: askının bittiği an ve kalıcı ban. */
    val askininBitisi = timestampWithTimeZone("askinin_bitisi").nullable()
    val banli = bool("banli").default(false)
    val yorumYasagiBitisi = timestampWithTimeZone("yorum_yasagi_bitisi").nullable()
    val gizli = bool("gizli").default(false)
    val kurator = bool("kurator").default(false)
    override val primaryKey = PrimaryKey(id)
}

private object KimlikTablosu : Table("kimlik") {
    val saglayici = text("saglayici")
    val haricKimlik = text("harici_kimlik")
    val kullaniciId = javaUUID("kullanici_id")
    override val primaryKey = PrimaryKey(saglayici, haricKimlik)
}
