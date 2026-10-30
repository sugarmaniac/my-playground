package com.sosyaloyun.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import kotlin.time.Clock

/**
 * Backend'in dışarıya dokunan her şeyi. Uygulama gerçeklerini [ortamdan] alır; testler
 * gerçek PostgreSQL ile sahte saat ve sahte dış servis adaptörlerini verir.
 */
class Bagimliliklar(
    val veriKaynagi: DataSource,
    val saat: Clock,
    val kimlikDogrulayici: KimlikDogrulayici,
    /** Betanın ilk kullanıcılarının katılacağı, sahibi olmayan davet kodları; açılışta eklenir. */
    val kurucuDavetKodlari: List<String> = emptyList(),
    /** Oyun kataloğunun kaynağı (IGDB); null ise katalog içe aktarılmaz (anahtar henüz yok). */
    val katalogKaynagi: KatalogKaynagi? = null,
    /** IGDB webhook isteklerinin `X-Secret` başlığında gelmesi gereken değer; null ise webhook'lar reddedilir. */
    val igdbWebhookSirri: String? = null,
    /** Açılışta ve sonra günde bir tam içe aktarma ve popülerlik güncellemesi; testlerde kapalı. */
    val katalogZamanlayici: Boolean = false,
    /** Steam Web API; null ise Steam bağlama kullanılamaz (anahtar henüz yok). */
    val steamKaynagi: SteamKaynagi? = null,
    /** Günde bir bağlı Steam hesaplarının süreleri güncellenir; testlerde kapalı. */
    val steamZamanlayici: Boolean = false,
) {
    companion object {
        /** PostgreSQL bağlantısı standart `PG*` ortam değişkenlerinden okunur (Railway ve VDS aynı imajı kullanır, ADR 0009). */
        fun ortamdan(): Bagimliliklar {
            fun ortam(ad: String, varsayilan: String) = System.getenv(ad)?.takeIf { it.isNotBlank() } ?: varsayilan
            val veriKaynagi = HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = "jdbc:postgresql://${ortam("PGHOST", "localhost")}:${ortam("PGPORT", "5432")}/${ortam("PGDATABASE", "sosyaloyun")}"
                    username = ortam("PGUSER", "sosyaloyun")
                    password = ortam("PGPASSWORD", "sosyaloyun")
                },
            )
            return Bagimliliklar(
                veriKaynagi = veriKaynagi,
                saat = Clock.System,
                kimlikDogrulayici = JwtKimlikDogrulayici.ortamdan(Clock.System),
                kurucuDavetKodlari = ortam("KURUCU_DAVET_KODLARI", "").split(",").map { it.trim() }.filter { it.isNotEmpty() },
                katalogKaynagi = IgdbKatalogKaynagi.ortamdan(),
                igdbWebhookSirri = System.getenv("IGDB_WEBHOOK_SIRRI")?.takeIf { it.isNotBlank() },
                steamKaynagi = SteamWebApiKaynagi.ortamdan(),
                steamZamanlayici = true,
                katalogZamanlayici = true,
            )
        }
    }
}
