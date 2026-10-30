package com.sosyaloyun.server

import com.sosyaloyun.shared.HaftalikPuanDegisimi
import com.sosyaloyun.shared.PuanDegisimi
import com.sosyaloyun.shared.PuanGecmisi
import com.sosyaloyun.shared.PuanHaftasi
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Puan geçmişi (Faz 2, #51): her hafta (pazartesi başlar, Türkiye saati) her puanlı oyunun topluluk puanı ve puan veren
 * sayısı bir kez kaydedilir. Anlık görüntü faz kapalıyken de alınır ki faz açılınca geçmiş hazır olsun; eşik okunurken
 * uygulanır, böylece eşik değişince geçmiş de ona uyar.
 */
class PuanGecmisleri(
    private val veritabani: Database,
    private val ayarlar: Ayarlar,
    private val katalog: Katalog,
    private val saat: Clock,
) {
    /** Bu haftanın görüntüsü alınmadıysa alır; alındıysa false. Zamanlayıcı saatte bir çağırır. */
    suspend fun anlikGoruntuAl(): Boolean {
        val hafta = buHafta()
        return withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val yeni = PuanHaftasiTablosu.insertIgnore { it[PuanHaftasiTablosu.hafta] = hafta }.insertedCount > 0
                if (yeni) {
                    // Topluluk puanıyla aynı hesap (Topluluk.puanlar): puan veren bütün kütüphane girişleri.
                    exec(
                        """
                        INSERT INTO puan_anlik_goruntusu (oyun_id, hafta, ortalama, puan_veren)
                        SELECT oyun_id, DATE '$hafta', ROUND(AVG(puan), 2), COUNT(puan)
                        FROM kutuphane_girisi WHERE puan IS NOT NULL GROUP BY oyun_id
                        ON CONFLICT DO NOTHING
                        """.trimIndent(),
                    )
                }
                yeni
            }
        }
    }

    /** Son [HAFTA_SAYISI] hafta, eskiden yeniye; görüntüsü olmayan ya da eşik altı haftalar puansız. */
    suspend fun gecmis(oyunId: UUID): PuanGecmisi {
        val esik = ayarlar.oku(Ayar.ToplulukPuaniEsigi)
        val haftalar = (HAFTA_SAYISI - 1 downTo 0).map { buHafta().minusWeeks(it.toLong()) }
        val satirlar = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                val p = PuanAnlikGoruntusuTablosu
                p.selectAll().where { (p.oyunId eq oyunId) and (p.hafta greaterEq haftalar.first()) }
                    .associate { it[p.hafta] to (it[p.ortalama].toDouble() to it[p.puanVeren]) }
            }
        }
        return PuanGecmisi(
            haftalar.map { hafta ->
                val (ortalama, sayi) = satirlar[hafta] ?: (null to 0)
                PuanHaftasi(hafta.toKotlinLocalDate(), ortalama?.takeIf { sayi >= esik }, sayi)
            },
        )
    }

    /** Bu haftanın ve geçen haftanın görüntüleri arasında puanı en çok artan ve azalan oyunlar; iki haftada da eşiği geçemeyen oyun dahil değil. */
    suspend fun haftalikDegisim(): HaftalikPuanDegisimi {
        val esik = ayarlar.oku(Ayar.ToplulukPuaniEsigi)
        val farklar = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                // Yalnızca bu hafta ile geçen hafta; biri eksikse (zamanlayıcı çalışmamışsa) kart boş kalır.
                val simdiki = buHafta()
                val onceki = simdiki.minusWeeks(1)
                val sonIki = listOf(simdiki, onceki)
                val alinan = PuanHaftasiTablosu.selectAll().where { PuanHaftasiTablosu.hafta inList sonIki }.count()
                if (alinan < 2) return@transaction emptyList()
                val p = PuanAnlikGoruntusuTablosu
                val puanlar = p.selectAll().where { (p.hafta inList sonIki) and (p.puanVeren greaterEq esik) }
                    .groupBy({ it[p.oyunId] }, { it[p.hafta] to it[p.ortalama].toDouble() })
                    .mapValues { it.value.toMap() }
                puanlar.mapNotNull { (oyun, haftalar) ->
                    val once = haftalar[onceki] ?: return@mapNotNull null
                    val simdi = haftalar[simdiki] ?: return@mapNotNull null
                    Triple(oyun, once, simdi).takeIf { simdi != once }
                }
            }
        }
        val artanlar = farklar.filter { it.third > it.second }.sortedByDescending { it.third - it.second }.take(KART_ADEDI)
        val azalanlar = farklar.filter { it.third < it.second }.sortedBy { it.third - it.second }.take(KART_ADEDI)
        val ozetler = katalog.ozetleri((artanlar + azalanlar).map { it.first })
        fun List<Triple<UUID, Double, Double>>.degisimler() =
            mapNotNull { (oyun, once, simdi) -> ozetler[oyun]?.let { PuanDegisimi(it, once, simdi) } }
        return HaftalikPuanDegisimi(artanlar.degisimler(), azalanlar.degisimler())
    }

    private fun buHafta(): LocalDate =
        saat.now().toJavaInstant().atZone(TURKIYE).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    private companion object {
        const val HAFTA_SAYISI = 12
        const val KART_ADEDI = 5
    }
}

internal object PuanAnlikGoruntusuTablosu : Table("puan_anlik_goruntusu") {
    val oyunId = javaUUID("oyun_id")
    val hafta = date("hafta")
    val ortalama = decimal("ortalama", 4, 2)
    val puanVeren = integer("puan_veren")
    override val primaryKey = PrimaryKey(oyunId, hafta)
}

internal object PuanHaftasiTablosu : Table("puan_anlik_goruntusu_haftasi") {
    val hafta = date("hafta")
    override val primaryKey = PrimaryKey(hafta)
}
