package com.sosyaloyun.server

import java.time.LocalDate

/**
 * Oyun kataloğunun dış kaynağı (ADR 0001). Tek uygulaması IGDB; aynı anda tek aktif kaynak var.
 * Kaynağı yalnızca içe aktarma çağırır; kullanıcı istekleri hiçbir zaman buraya gelmez.
 */
interface KatalogKaynagi {
    /** Kaynak kimliği [sonKaynakId]'den büyük oyunlar, kimliğe göre artan sırada. */
    suspend fun oyunSayfasi(sonKaynakId: Long, adet: Int): List<KaynakOyun>

    /** Belirli oyunların güncel hali; webhook'lar yalnızca kimlik taşıdığı için oyun buradan yeniden okunur. */
    suspend fun oyunlar(kaynakIdleri: List<Long>): List<KaynakOyun>

    /** Oyunların popülerlik (PopScore) değerleri, oyunun kaynak kimliğine göre artan sırada. */
    suspend fun populerlikSayfasi(sonKaynakId: Long, adet: Int): List<KaynakPopulerlik>

    /** En popüler [adet] oyunun popülerliği, yüksekten düşüğe; içe aktarma bunları önce alır. */
    suspend fun enPopulerler(adet: Int): List<KaynakPopulerlik>
}

/** Kaynağın kendi sınıflandırması; hangisinin ayrı oyun olacağına içe aktarma karar verir. */
enum class KaynakTuru {
    ANA_OYUN, DLC, GENISLEME, BAGIMSIZ_GENISLEME, REMAKE, REMASTER,
    /** Başka platforma taşınmış aynı oyun: ana oyunda birleşir. */
    PORT,
    /** Genişletilmiş sürüm (ör. GOTY): ana oyunda birleşir. */
    GENISLETILMIS_OYUN,
    PAKET, MOD, BOLUM, SEZON, GUNCELLEME, CATAL, DIGER,
}

data class KaynakOyun(
    val kaynakId: Long,
    val ad: String,
    val tur: KaynakTuru,
    /** DLC, genişleme ve port'ların bağlı olduğu oyun (IGDB `parent_game`). */
    val anaOyunKaynakId: Long? = null,
    /** Sürümlerin (edition) ana sürümü (IGDB `version_parent`). */
    val surumAnasiKaynakId: Long? = null,
    val cikisTarihi: LocalDate? = null,
    val kapakKimligi: String? = null,
    val platformlar: List<String> = emptyList(),
    val steamUygulamaIdleri: List<Long> = emptyList(),
    val ozet: String? = null,
    /** IGDB'deki sayfasının kısa adı (`igdb.com/games/<slug>`), atıf linki için. */
    val slug: String? = null,
    val gelistiriciler: List<String> = emptyList(),
    val yayincilar: List<String> = emptyList(),
    val turler: List<String> = emptyList(),
    val magazaLinkleri: List<KaynakMagazaLinki> = emptyList(),
)

data class KaynakMagazaLinki(val magaza: com.sosyaloyun.shared.Magaza, val url: String)

data class KaynakPopulerlik(val oyunKaynakId: Long, val deger: Double)
