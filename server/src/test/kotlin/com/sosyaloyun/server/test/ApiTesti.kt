package com.sosyaloyun.server.test

import com.sosyaloyun.server.Ayarlar
import com.sosyaloyun.server.DavetKodlari
import com.sosyaloyun.server.Katalog
import com.sosyaloyun.server.KatalogIceAktarici
import com.sosyaloyun.server.Kutuphane
import com.sosyaloyun.server.PushDagitici
import com.sosyaloyun.server.Steam
import com.sosyaloyun.server.Bagimliliklar
import com.sosyaloyun.server.modul
import com.sosyaloyun.server.veritabaniniHazirla
import com.sosyaloyun.shared.ApiJson
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.DavetKoduKullanIstegi
import com.sosyaloyun.shared.GirisIstegi
import com.sosyaloyun.shared.GirisYaniti
import com.sosyaloyun.shared.KimlikSaglayici
import com.sosyaloyun.shared.ProfilIstegi
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.KSerializer
import io.ktor.server.testing.testApplication

/** Bir API testinin eriştiği her şey: gerçek HTTP istemcisi, sahte saat, sahte adaptörler ve testte değiştirilebilen ayarlar. */
class ApiTestOrtami(
    val client: HttpClient,
    val saat: SahteSaat,
    val ayarlar: Ayarlar,
    val kimlikDogrulayici: SahteKimlikDogrulayici,
    private val davetKodlari: DavetKodlari,
    val katalogKaynagi: SahteKatalogKaynagi,
    /** Kataloğu sahte kaynaktan içeri alır; gerçekte açılışta ve zamanlanmış olarak çalışır. */
    val katalogIceAktarici: KatalogIceAktarici,
    val steamKaynagi: SahteSteamKaynagi,
    /** Steam sürelerinin günlük güncellemesi gerçekte zamanlanmış çalışır; testte [Steam.hepsiniGuncelle] elle çağrılır. */
    val steam: Steam,
    val pushGonderici: SahtePushGonderici,
    /** Push'lar gerçekte arka planda gönderilir; testte [PushDagitici.bekleyenleriGonder] elle çağrılır. */
    val pushDagitici: PushDagitici,
) {
    private var kurucuKoduSayaci = 0

    /** Sahibi olmayan tek kullanımlık bir kurucu davet kodu ekler (betanın ilk kullanıcıları için). */
    suspend fun kurucuKoduEkle(kod: String = "KURUCU${++kurucuKoduSayaci}"): String {
        davetKodlari.kurucuKodlariniEkle(listOf(kod))
        return kod
    }

    /** Giriş yapmış ve bir davet koduyla betaya katılmış, henüz kullanıcı adı seçmemiş kullanıcı. */
    suspend fun davetliKullanici(haricKimlik: String = "kullanici-1"): GirisYaniti {
        val giris = girisYap(haricKimlik)
        val yanit = client.post(ApiYollari.DAVET_KULLAN) {
            bearerAuth(giris.oturumTokeni)
            json(DavetKoduKullanIstegi.serializer(), DavetKoduKullanIstegi(kurucuKoduEkle()))
        }
        check(yanit.status == HttpStatusCode.NoContent) { "davet kodu kullanılamadı: ${yanit.status}" }
        return giris
    }

    /** Onboarding'i bitirmiş kullanıcı: davetli ve kullanıcı adı seçilmiş. */
    suspend fun kayitliKullanici(kullaniciAdi: String, haricKimlik: String = kullaniciAdi): GirisYaniti {
        val giris = davetliKullanici(haricKimlik)
        val yanit = client.put(ApiYollari.PROFIL) {
            bearerAuth(giris.oturumTokeni)
            json(ProfilIstegi.serializer(), ProfilIstegi(kullaniciAdi, kullaniciAdi.replaceFirstChar { it.uppercase() }, Avatar.KUMANDA))
        }
        check(yanit.status == HttpStatusCode.OK) { "profil kaydedilemedi: ${yanit.status}" }
        return giris
    }

    /** Sahte bir Apple/Google kimliğiyle giriş yapar; dönen oturum token'ı `bearerAuth` ile kullanılır. */
    suspend fun girisYap(
        haricKimlik: String = "kullanici-1",
        saglayici: KimlikSaglayici = KimlikSaglayici.APPLE,
    ): GirisYaniti {
        val yanit = client.post(ApiYollari.OTURUM) {
            json(GirisIstegi.serializer(), GirisIstegi(saglayici, kimlikDogrulayici.tokenUret(saglayici, haricKimlik)))
        }
        return ApiJson.decodeFromString(GirisYaniti.serializer(), yanit.bodyAsText())
    }
}

const val TEST_WEBHOOK_SIRRI = "test-webhook-sirri"

/** İsteğin gövdesini ortak modeldeki tipiyle JSON olarak yazar. */
fun <T> HttpRequestBuilder.json(serializer: KSerializer<T>, govde: T) {
    contentType(ContentType.Application.Json)
    setBody(ApiJson.encodeToString(serializer, govde))
}

/** Yanıt gövdesini ortak modeldeki tipiyle okur. */
suspend fun <T> HttpResponse.govde(serializer: KSerializer<T>): T = ApiJson.decodeFromString(serializer, bodyAsText())

/**
 * Backend'i gerçek PostgreSQL'e (Testcontainers) karşı ayağa kaldırıp [blok]'u çalıştırır.
 * Her test boş bir veritabanıyla başlar; dış servisler ve saat sahtedir.
 */
fun apiTesti(blok: suspend ApiTestOrtami.() -> Unit) = testApplication {
    val veritabani = veritabaniniHazirla(TestVeritabani.kaynak)
    TestVeritabani.temizle()
    val saat = SahteSaat()
    val kimlikDogrulayici = SahteKimlikDogrulayici()
    val katalogKaynagi = SahteKatalogKaynagi()
    val steamKaynagi = SahteSteamKaynagi()
    val pushGonderici = SahtePushGonderici()
    val bagimliliklar = Bagimliliklar(
        veriKaynagi = TestVeritabani.kaynak,
        saat = saat,
        kimlikDogrulayici = kimlikDogrulayici,
        katalogKaynagi = katalogKaynagi,
        igdbWebhookSirri = TEST_WEBHOOK_SIRRI,
        steamKaynagi = steamKaynagi,
    )
    application { modul(bagimliliklar) }

    ApiTestOrtami(
        client = client,
        saat = saat,
        ayarlar = Ayarlar(veritabani),
        kimlikDogrulayici = kimlikDogrulayici,
        davetKodlari = DavetKodlari(veritabani, saat),
        katalogKaynagi = katalogKaynagi,
        katalogIceAktarici = KatalogIceAktarici(katalogKaynagi, Katalog(veritabani)),
        steamKaynagi = steamKaynagi,
        steam = Steam(veritabani, Katalog(veritabani), Kutuphane(veritabani, saat), steamKaynagi, saat),
        pushGonderici = pushGonderici,
        pushDagitici = PushDagitici(veritabani, Ayarlar(veritabani), pushGonderici, saat),
    ).blok()
}
