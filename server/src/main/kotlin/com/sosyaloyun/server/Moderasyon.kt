package com.sosyaloyun.server

import com.sosyaloyun.shared.BildirimTuru
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.SikayetIstegi
import com.sosyaloyun.shared.SikayetKurallari
import com.sosyaloyun.shared.SikayetSebebi
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory

/** Şikayet edilen şey; kayıt ya da yorum silinse de şikayetin hedefinin türü bilinir. */
enum class SikayetHedefi { KAYIT, PROFIL, YORUM }

/** Şikayetin moderatör kararı; [ACIK] karar bekleyen. */
enum class SikayetDurumu { ACIK, REDDEDILDI, ICERIK_KALDIRILDI, ASKIYA_ALINDI, BANLANDI }

/** Moderasyon listesindeki bir şikayet; şikayet eden yalnızca burada, moderatöre görünür. */
data class AcikSikayet(
    val id: UUID,
    val zaman: OffsetDateTime,
    val sebep: SikayetSebebi,
    val aciklama: String?,
    val sikayetEden: String?,
    val hedef: String?,
    val tur: SikayetHedefi,
    val icerik: String?,
)

/** Hesabın girişe kapalı olma nedeni. */
sealed interface Yaptirim {
    data class Askida(val bitis: OffsetDateTime) : Yaptirim
    data object Banli : Yaptirim
}

class Moderasyon(
    private val veritabani: Database,
    private val ayarlar: Ayarlar,
    private val eposta: EpostaGonderici?,
    private val saat: Clock,
) {
    /** Geçersiz istekte hata kodu döner. Aynı kişinin aynı hedefe açık şikayeti varsa yenisi açılmaz. */
    suspend fun sikayetEt(sikayetEden: UUID, istek: SikayetIstegi): HataKodu? {
        val aciklama = istek.aciklama?.trim()?.takeIf { it.isNotEmpty() }
        if (aciklama != null && aciklama.length > SikayetKurallari.ACIKLAMA_EN_FAZLA) return HataKodu.SIKAYET_GECERSIZ
        if (listOfNotNull(istek.gunlukKaydiId, istek.kullaniciAdi, istek.yorumId).size != 1) return HataKodu.SIKAYET_GECERSIZ
        val sonuc = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val hedef = hedefiBul(sikayetEden, istek) ?: return@transaction HataKodu.SIKAYET_GECERSIZ to null
                if (hedef.kullanici == sikayetEden) return@transaction HataKodu.SIKAYET_GECERSIZ to null
                val zatenAcik = SikayetTablosu.selectAll().where {
                    val kosul = (SikayetTablosu.sikayetEden eq sikayetEden) and (SikayetTablosu.hedefKullanici eq hedef.kullanici) and
                        (SikayetTablosu.durum eq SikayetDurumu.ACIK.name) and (SikayetTablosu.hedefTuru eq hedef.tur.name)
                    when (hedef.tur) {
                        SikayetHedefi.KAYIT -> kosul and (SikayetTablosu.gunlukKaydiId eq hedef.gunlukKaydi!!)
                        SikayetHedefi.YORUM -> kosul and (SikayetTablosu.yorumId eq hedef.yorum!!)
                        SikayetHedefi.PROFIL -> kosul
                    }
                }.any()
                if (zatenAcik) return@transaction null to null
                SikayetTablosu.insert {
                    it[id] = UUID.randomUUID()
                    it[SikayetTablosu.sikayetEden] = sikayetEden
                    it[hedefKullanici] = hedef.kullanici
                    it[gunlukKaydiId] = hedef.gunlukKaydi
                    it[yorumId] = hedef.yorum
                    it[hedefTuru] = hedef.tur.name
                    it[sebep] = istek.sebep.name
                    it[SikayetTablosu.aciklama] = aciklama
                    it[icerikOzeti] = hedef.ozet
                    it[olusturulma] = simdi()
                }
                null to "Sebep: ${istek.sebep.name}\nİçerik: ${hedef.ozet.orEmpty()}\nAçıklama: ${aciklama.orEmpty()}"
            }
        }
        sonuc.second?.let { metin ->
            try {
                eposta?.gonder("Yeni şikayet", "$metin\n\nYönetim paneli: /yonetim")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                log.warn("Şikayet e-postası gönderilemedi", e)
            }
        }
        return sonuc.first
    }

    private class Hedef(val tur: SikayetHedefi, val kullanici: UUID, val gunlukKaydi: UUID?, val yorum: UUID?, val ozet: String?)

    /** Şikayet edenin göremediği içerik şikayet edilemez; görebildiği her şey edilebilir. */
    private fun JdbcTransaction.hedefiBul(sikayetEden: UUID, istek: SikayetIstegi): Hedef? {
        istek.gunlukKaydiId?.let { ham ->
            val id = runCatching { UUID.fromString(ham) }.getOrNull() ?: return null
            val satir = GunlukKaydiTablosu.selectAll().where { GunlukKaydiTablosu.id eq id }.singleOrNull() ?: return null
            val sahip = satir[GunlukKaydiTablosu.kullaniciId]
            if (sahip in engellileri(sikayetEden)) return null
            return Hedef(SikayetHedefi.KAYIT, sahip, id, null, satir[GunlukKaydiTablosu.inceleme])
        }
        istek.yorumId?.let { ham ->
            val id = runCatching { UUID.fromString(ham) }.getOrNull() ?: return null
            val satir = YorumTablosu.selectAll().where { YorumTablosu.id eq id }.singleOrNull() ?: return null
            val yazar = satir[YorumTablosu.kullaniciId]
            if (yazar in engellileri(sikayetEden)) return null
            return Hedef(SikayetHedefi.YORUM, yazar, satir[YorumTablosu.gunlukKaydiId], id, satir[YorumTablosu.metin])
        }
        val ad = istek.kullaniciAdi ?: return null
        val satir = KullaniciTablosu.selectAll().where { KullaniciTablosu.kullaniciAdi eq ad }.singleOrNull() ?: return null
        val kullanici = satir[KullaniciTablosu.id]
        if (kullanici in engellileri(sikayetEden)) return null
        return Hedef(SikayetHedefi.PROFIL, kullanici, null, null, "${satir[KullaniciTablosu.gorunenAd]} (@$ad): ${satir[KullaniciTablosu.biyografi].orEmpty()}")
    }

    suspend fun acikSikayetler(): List<AcikSikayet> = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            SikayetTablosu.selectAll().where { SikayetTablosu.durum eq SikayetDurumu.ACIK.name }
                .orderBy(SikayetTablosu.olusturulma to SortOrder.ASC)
                .map { satir ->
                    fun ad(id: UUID) = KullaniciTablosu.selectAll().where { KullaniciTablosu.id eq id }.single()[KullaniciTablosu.kullaniciAdi]
                    AcikSikayet(
                        id = satir[SikayetTablosu.id],
                        zaman = satir[SikayetTablosu.olusturulma],
                        sebep = SikayetSebebi.valueOf(satir[SikayetTablosu.sebep]),
                        aciklama = satir[SikayetTablosu.aciklama],
                        sikayetEden = ad(satir[SikayetTablosu.sikayetEden]),
                        hedef = ad(satir[SikayetTablosu.hedefKullanici]),
                        tur = SikayetHedefi.valueOf(satir[SikayetTablosu.hedefTuru]),
                        icerik = satir[SikayetTablosu.icerikOzeti],
                    )
                }
        }
    }

    /**
     * Kararı uygular ve kalıp mesajları yazar. İçerik kaldırma: günlük kaydı silinir (beğenileri, akış olayı ve
     * bildirimleriyle); profil şikayetinde biyografi silinir, görünen ad kullanıcı adına döner. Askı ve ban bütün
     * oturumları kapatır. Aynı hedefe ait diğer açık şikayetler de aynı kararla kapanır. Açık şikayet yoksa false.
     */
    suspend fun karar(sikayetId: UUID, karar: SikayetDurumu): Boolean {
        require(karar != SikayetDurumu.ACIK)
        val askiGun = ayarlar.oku(Ayar.AskiSuresiGun)
        return withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val sikayet = SikayetTablosu.selectAll()
                    .where { (SikayetTablosu.id eq sikayetId) and (SikayetTablosu.durum eq SikayetDurumu.ACIK.name) }
                    .singleOrNull() ?: return@transaction false
                val hedef = sikayet[SikayetTablosu.hedefKullanici]
                val tur = SikayetHedefi.valueOf(sikayet[SikayetTablosu.hedefTuru])
                val kayit = sikayet[SikayetTablosu.gunlukKaydiId]
                val yorum = sikayet[SikayetTablosu.yorumId]
                val simdi = simdi()
                // Aynı hedefle ilgili açık şikayetler aynı kararla kapanır; her şikayet edene mesaj gider. Kayıt silinince
                // gunluk_kaydi_id boşaldığından kayıt şikayetleri silmeden önce toplanır, yoksa profil şikayeti gibi görünürler.
                val ilgili = SikayetTablosu.selectAll().where {
                    val acik = (SikayetTablosu.hedefKullanici eq hedef) and (SikayetTablosu.durum eq SikayetDurumu.ACIK.name)
                    when {
                        karar == SikayetDurumu.ICERIK_KALDIRILDI && tur == SikayetHedefi.YORUM && yorum != null -> acik and (SikayetTablosu.yorumId eq yorum)
                        karar == SikayetDurumu.ICERIK_KALDIRILDI && tur == SikayetHedefi.KAYIT && kayit != null ->
                            acik and (SikayetTablosu.hedefTuru eq SikayetHedefi.KAYIT.name) and (SikayetTablosu.gunlukKaydiId eq kayit)
                        karar == SikayetDurumu.ICERIK_KALDIRILDI || karar == SikayetDurumu.REDDEDILDI -> acik and (SikayetTablosu.id eq sikayetId)
                        else -> acik
                    }
                }.toList()
                when (karar) {
                    SikayetDurumu.ICERIK_KALDIRILDI -> {
                        when (tur) {
                            SikayetHedefi.KAYIT -> kayit?.let { k -> GunlukKaydiTablosu.deleteWhere { GunlukKaydiTablosu.id eq k } }
                            SikayetHedefi.YORUM -> yorum?.let { y -> YorumTablosu.deleteWhere { YorumTablosu.id eq y } }
                            SikayetHedefi.PROFIL -> KullaniciTablosu.update({ KullaniciTablosu.id eq hedef }) {
                                it[biyografi] = null
                                it[gorunenAd] = KullaniciTablosu.kullaniciAdi
                            }
                        }
                        with(BildirimOlaylari) { kalipMesaj(hedef, BildirimTuru.ICERIK_KALDIRILDI, simdi) }
                    }
                    SikayetDurumu.ASKIYA_ALINDI, SikayetDurumu.BANLANDI -> {
                        KullaniciTablosu.update({ KullaniciTablosu.id eq hedef }) {
                            if (karar == SikayetDurumu.BANLANDI) it[banli] = true
                            else it[askininBitisi] = (saat.now() + askiGun.days).toJavaInstant().atOffset(ZoneOffset.UTC)
                        }
                        oturumlariKapat(hedef)
                    }
                    else -> Unit
                }
                ilgili.forEach { satir ->
                    SikayetTablosu.update({ SikayetTablosu.id eq satir[SikayetTablosu.id] }) {
                        it[durum] = karar.name
                        it[kararZamani] = simdi
                    }
                    with(BildirimOlaylari) { kalipMesaj(satir[SikayetTablosu.sikayetEden], BildirimTuru.SIKAYET_SONUCLANDI, simdi) }
                }
                true
            }
        }
    }

    /** Askıdaki ya da banlı hesap için yaptırım; askı süresi dolmuşsa null. */
    suspend fun yaptirim(kullaniciId: UUID): Yaptirim? = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val satir = KullaniciTablosu.selectAll().where { KullaniciTablosu.id eq kullaniciId }.single()
            when {
                satir[KullaniciTablosu.banli] -> Yaptirim.Banli
                else -> satir[KullaniciTablosu.askininBitisi]?.takeIf { it.isAfter(simdi()) }?.let { Yaptirim.Askida(it) }
            }
        }
    }

    private fun simdi(): OffsetDateTime = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)

    private companion object {
        val log = LoggerFactory.getLogger(Moderasyon::class.java)
    }
}

internal object SikayetTablosu : Table("sikayet") {
    val id = javaUUID("id")
    val sikayetEden = javaUUID("sikayet_eden")
    val hedefKullanici = javaUUID("hedef_kullanici")
    val gunlukKaydiId = javaUUID("gunluk_kaydi_id").nullable()
    val yorumId = javaUUID("yorum_id").nullable()
    val hedefTuru = text("hedef_turu")
    val sebep = text("sebep")
    val aciklama = text("aciklama").nullable()
    val icerikOzeti = text("icerik_ozeti").nullable()
    val olusturulma = timestampWithTimeZone("olusturulma")
    val durum = text("durum").default(SikayetDurumu.ACIK.name)
    val kararZamani = timestampWithTimeZone("karar_zamani").nullable()
    override val primaryKey = PrimaryKey(id)
}
