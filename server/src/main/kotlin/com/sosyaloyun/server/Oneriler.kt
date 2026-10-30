package com.sosyaloyun.server

import com.sosyaloyun.shared.Oneri
import com.sosyaloyun.shared.OneriListesi
import com.sosyaloyun.shared.OneriNedeni
import java.time.ZoneOffset
import java.util.UUID
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Kişisel öneriler (Faz 3, #56), yalnızca kendi veritabanımızdan:
 * - **Benzer kullanıcılar:** en az [ORTAK_EN_AZ] ortak oyunu benzer puanlayan kullanıcıların yüksek puanladığı oyunlar.
 * - **Türler ve platformlar:** sevdiği türlerdeki ve kullandığı platformlardaki popüler oyunlar.
 * - Hiç verisi olmayan yeni kullanıcıya popüler oyunlar.
 * Kütüphanedeki, günlüğe yazılmış ve "ilgilenmiyorum" denen oyunlar önerilmez.
 */
class Oneriler(private val veritabani: Database, private val katalog: Katalog, private val saat: Clock) {
    suspend fun oneriler(kullanici: UUID): OneriListesi {
        val profil = profil(kullanici)
        val secilenler = linkedMapOf<UUID, Pair<OneriNedeni, String?>>()
        fun ekle(idler: List<UUID>, neden: OneriNedeni, ayrinti: String? = null, adet: Int = ADET) {
            idler.filter { it !in profil.haric && it !in secilenler }.take(adet).forEach { secilenler[it] = neden to ayrinti }
        }
        ekle(benzerKullanicilarinkiler(kullanici, profil), OneriNedeni.BENZER_KULLANICILAR, adet = BENZER_EN_FAZLA)
        // Her adım hariçlere o ana kadar seçilenleri de katar ve toplamı ADET'te tutar.
        fun kalan(enFazla: Int) = minOf(enFazla, ADET - secilenler.size)
        for (tur in profil.sevilenTurler) {
            if (kalan(TUR_BASINA) <= 0) break
            ekle(katalog.populerAdaylar(tur, profil.platformlar, profil.haric + secilenler.keys, kalan(TUR_BASINA)), OneriNedeni.TUR, tur, kalan(TUR_BASINA))
        }
        for (platform in profil.platformlar) {
            if (kalan(TUR_BASINA) <= 0) break
            ekle(katalog.populerAdaylar(null, listOf(platform), profil.haric + secilenler.keys, kalan(TUR_BASINA)), OneriNedeni.PLATFORM, platform, kalan(TUR_BASINA))
        }
        if (kalan(ADET) > 0) {
            ekle(katalog.populerAdaylar(null, emptyList(), profil.haric + secilenler.keys, kalan(ADET)), OneriNedeni.POPULER, adet = kalan(ADET))
        }
        val ozetler = katalog.ozetleri(secilenler.keys.toList())
        return OneriListesi(secilenler.mapNotNull { (id, neden) -> ozetler[id]?.let { Oneri(it, neden.first, neden.second) } })
    }

    /** "İlgilenmiyorum": oyun bu kullanıcıya bir daha önerilmez. */
    suspend fun gizle(kullanici: UUID, oyun: UUID) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                OneriGizlemeTablosu.insertIgnore {
                    it[kullaniciId] = kullanici
                    it[oyunId] = oyun
                    it[olusturulma] = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)
                }
            }
        }
    }

    private class Profil(
        val puanlar: Map<UUID, Int>,
        val haric: Set<UUID>,
        val sevilenTurler: List<String>,
        val platformlar: List<String>,
    )

    private suspend fun profil(kullanici: UUID): Profil {
        val (puanlar, sevilenler, haric, platformlar) = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val k = KutuphaneGirisiTablosu
                val girisler = k.selectAll().where { k.kullaniciId eq kullanici }.toList()
                val puanlar = girisler.mapNotNull { g -> g[k.puan]?.let { g[k.oyunId] to it.toInt() } }.toMap()
                // Sevilen: yüksek puanlı, beğenilen ya da bitirilen oyunlar.
                val sevilenler = girisler.filter { g -> (g[k.puan]?.toInt() ?: 0) >= SEVILEN_PUAN || g[k.begendim] || g[k.durum] == "BITIRDIM" }.map { it[k.oyunId] }
                val gunluk = GunlukKaydiTablosu.select(GunlukKaydiTablosu.oyunId, GunlukKaydiTablosu.platform)
                    .where { GunlukKaydiTablosu.kullaniciId eq kullanici }.toList()
                val gizlenen = OneriGizlemeTablosu.select(OneriGizlemeTablosu.oyunId).where { OneriGizlemeTablosu.kullaniciId eq kullanici }
                    .map { it[OneriGizlemeTablosu.oyunId] }
                val sonPlatform = KullaniciTablosu.select(KullaniciTablosu.sonPlatform).where { KullaniciTablosu.id eq kullanici }
                    .singleOrNull()?.get(KullaniciTablosu.sonPlatform)
                val platformlar = (listOfNotNull(sonPlatform) + gunluk.mapNotNull { it[GunlukKaydiTablosu.platform] })
                    .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(PLATFORM_EN_FAZLA)
                HamProfil(puanlar, sevilenler, girisler.map { it[k.oyunId] }.toSet() + gunluk.map { it[GunlukKaydiTablosu.oyunId] } + gizlenen, platformlar)
            }
        }
        val turler = katalog.turleri(sevilenler).values.flatten()
            .groupingBy { it }.eachCount().entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }.take(TUR_EN_FAZLA)
        return Profil(puanlar, haric, turler, platformlar)
    }

    private data class HamProfil(val puanlar: Map<UUID, Int>, val sevilenler: List<UUID>, val haric: Set<UUID>, val platformlar: List<String>)

    /**
     * Ortak oyunlarda puan farkı ortalaması küçük kullanıcılar benzerdir: benzerlik = 1 − ortalama|fark| / 9. Benzer
     * kullanıcıların [SEVILEN_PUAN] ve üstü verdiği oyunlar benzerliklerinin toplamına göre sıralanır.
     */
    private suspend fun benzerKullanicilarinkiler(kullanici: UUID, profil: Profil): List<UUID> {
        if (profil.puanlar.size < ORTAK_EN_AZ) return emptyList()
        return withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val k = KutuphaneGirisiTablosu
                val digerleri = k.select(k.kullaniciId, k.oyunId, k.puan)
                    .where { (k.oyunId inList profil.puanlar.keys) and k.puan.isNotNull() and (k.kullaniciId neq kullanici) }
                    .groupBy({ it[k.kullaniciId] }, { it[k.oyunId] to it[k.puan]!!.toInt() })
                val benzerlik = digerleri.mapNotNull { (kisi, ortak) ->
                    if (ortak.size < ORTAK_EN_AZ) return@mapNotNull null
                    val fark = ortak.map { (oyun, puan) -> abs(puan - profil.puanlar.getValue(oyun)) }.average()
                    (1 - fark / 9).takeIf { it >= BENZERLIK_ESIGI }?.let { kisi to it }
                }.sortedByDescending { it.second }.take(BENZER_KISI).toMap()
                if (benzerlik.isEmpty()) return@transaction emptyList()
                k.select(k.kullaniciId, k.oyunId).where { (k.kullaniciId inList benzerlik.keys) and (k.puan greaterEq SEVILEN_PUAN.toShort()) }
                    .groupBy({ it[k.oyunId] }, { benzerlik.getValue(it[k.kullaniciId]) })
                    .filterKeys { it !in profil.haric }
                    .entries.sortedByDescending { it.value.sum() }.map { it.key }
            }
        }
    }

    private companion object {
        const val ADET = 10
        const val BENZER_EN_FAZLA = 5
        const val TUR_BASINA = 3
        const val TUR_EN_FAZLA = 3
        const val PLATFORM_EN_FAZLA = 2
        const val SEVILEN_PUAN = 8
        const val ORTAK_EN_AZ = 3
        const val BENZER_KISI = 20
        const val BENZERLIK_ESIGI = 0.7
    }
}

internal object OneriGizlemeTablosu : Table("oneri_gizleme") {
    val kullaniciId = javaUUID("kullanici_id")
    val oyunId = javaUUID("oyun_id")
    val olusturulma = timestampWithTimeZone("olusturulma")
    override val primaryKey = PrimaryKey(kullaniciId, oyunId)
}
