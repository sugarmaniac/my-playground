package com.sosyaloyun.server

import com.sosyaloyun.shared.BildirimTuru
import com.sosyaloyun.shared.DavetKodu
import com.sosyaloyun.shared.DavetKoduKurallari
import java.security.SecureRandom
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** Kapalı betanın tek kullanımlık davet kodları (CONTEXT.md: Davet kodu). */
class DavetKodlari(private val veritabani: Database, private val saat: Clock = Clock.System) {
    enum class KullanmaSonucu { KATILDI, ZATEN_KATILMIS, GECERSIZ, KULLANILMIS }

    private val rastgele = SecureRandom()

    /** Sahibi olmayan kodlar: betanın ilk kullanıcıları bunlarla katılır. Var olan kod değişmez. */
    suspend fun kurucuKodlariniEkle(kodlar: List<String>) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                kodlar.map(DavetKoduKurallari::duzenle).filter { it.isNotEmpty() }.forEach { kod ->
                    DavetKoduTablosu.insertIgnore { it[DavetKoduTablosu.kod] = kod }
                }
            }
        }
    }

    suspend fun kullan(kullaniciId: UUID, hamKod: String): KullanmaSonucu = withContext(Dispatchers.IO) {
        val kod = DavetKoduKurallari.duzenle(hamKod)
        val simdi = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)
        transaction(veritabani) {
            // Aynı kullanıcının eşzamanlı istekleri sıraya girer; kullanıcı en fazla bir kod tüketir.
            val kullanici = KullaniciTablosu.selectAll().where { KullaniciTablosu.id eq kullaniciId }.forUpdate().single()
            if (kullanici[KullaniciTablosu.davetKabulZamani] != null) return@transaction KullanmaSonucu.ZATEN_KATILMIS

            val sahibi = DavetKoduTablosu.selectAll().where { DavetKoduTablosu.kod eq kod }.singleOrNull()?.get(DavetKoduTablosu.sahibiId)
            val tuketildi = DavetKoduTablosu.update({
                (DavetKoduTablosu.kod eq kod) and DavetKoduTablosu.kullanilma.isNull()
            }) {
                it[kullananId] = kullaniciId
                it[kullanilma] = simdi
            }
            if (tuketildi == 0) {
                val varMi = DavetKoduTablosu.selectAll().where { DavetKoduTablosu.kod eq kod }.any()
                return@transaction if (varMi) KullanmaSonucu.KULLANILMIS else KullanmaSonucu.GECERSIZ
            }
            KullaniciTablosu.update({ KullaniciTablosu.id eq kullaniciId }) { it[davetKabulZamani] = simdi }
            // Kodu kullanan, kodu vereni otomatik olarak takip eder (CONTEXT.md: Davet kodu).
            sahibi?.let {
                takipEtIcinde(kullaniciId, it, saat)
                with(BildirimOlaylari) { bildir(it, BildirimTuru.DAVET_KODU_KULLANILDI, kullaniciId, simdi) }
            }
            KullanmaSonucu.KATILDI
        }
    }

    /** Kullanıcının kodları; ayardaki sayıdan azsa eksikler üretilir (ayar sonradan artırılabilir). */
    suspend fun kodlari(sahibi: UUID, olmasiGereken: Int): List<DavetKodu> = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            // Aynı kullanıcının eşzamanlı istekleri sıraya girer; eksik kodlar bir kez üretilir.
            KullaniciTablosu.selectAll().where { KullaniciTablosu.id eq sahibi }.forUpdate().single()
            val mevcut = DavetKoduTablosu.selectAll().where { DavetKoduTablosu.sahibiId eq sahibi }.count().toInt()
            var eksik = olmasiGereken - mevcut
            while (eksik > 0) {
                val eklendi = DavetKoduTablosu.insertIgnore {
                    it[kod] = yeniKod()
                    it[sahibiId] = sahibi
                }.insertedCount
                eksik -= eklendi
            }

            DavetKoduTablosu
                .join(KullaniciTablosu, JoinType.LEFT, DavetKoduTablosu.kullananId, KullaniciTablosu.id)
                .selectAll()
                .where { DavetKoduTablosu.sahibiId eq sahibi }
                .orderBy(DavetKoduTablosu.olusturulma to SortOrder.ASC, DavetKoduTablosu.kod to SortOrder.ASC)
                .map { satir ->
                    DavetKodu(
                        kod = satir[DavetKoduTablosu.kod],
                        kullanan = if (satir.getOrNull(KullaniciTablosu.id) != null) satir.kullaniciOzeti() else null,
                        kullanildi = satir[DavetKoduTablosu.kullanilma] != null,
                    )
                }
        }
    }

    /** Karışabilecek karakterler (0/O, 1/I/L) olmadan 8 karakter. */
    private fun yeniKod(): String = String(CharArray(8) { ALFABE[rastgele.nextInt(ALFABE.length)] })

    private companion object {
        const val ALFABE = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    }
}

private object DavetKoduTablosu : Table("davet_kodu") {
    val kod = text("kod")
    val sahibiId = javaUUID("sahibi_id").nullable()
    val kullananId = javaUUID("kullanan_id").nullable()
    val kullanilma = timestampWithTimeZone("kullanilma").nullable()
    val olusturulma = timestampWithTimeZone("olusturulma")
    override val primaryKey = PrimaryKey(kod)
}
