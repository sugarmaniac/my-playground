package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.AkisSayfasi
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.BildirimTercihleri
import com.sosyaloyun.shared.BildirimTuru
import com.sosyaloyun.shared.CihazKaydiIstegi
import com.sosyaloyun.shared.CihazPlatformu
import com.sosyaloyun.shared.DavetKodlarimYaniti
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.HesapSilme
import com.sosyaloyun.shared.HesapSilmeIstegi
import com.sosyaloyun.shared.KullaniciListesi
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.OyunDetayi
import com.sosyaloyun.shared.SikayetIstegi
import com.sosyaloyun.shared.SikayetSebebi
import com.sosyaloyun.shared.SteamBaglaIstegi
import com.sosyaloyun.shared.TelefonNumarasi
import com.sosyaloyun.shared.TelefonumIstegi
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class HesapSilmeTest {
    private val tokenler = mutableMapOf<String, String>()
    private val kimlikler = mutableMapOf<String, String>()
    private fun t(ad: String) = tokenler.getValue(ad)
    private lateinit var oyun: String

    private suspend fun ApiTestOrtami.hazirla(vararg adlar: String) {
        adlar.forEach { ad ->
            val giris = kayitliKullanici(ad)
            tokenler[ad] = giris.oturumTokeni
            kimlikler[ad] = giris.kullaniciId
        }
        katalogKaynagi.oyunlar += KaynakOyun(1, "Silme Oyunu", KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()
        oyun = client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(t(adlar.first()))
            parameter("q", "silme")
        }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
    }

    private suspend fun ApiTestOrtami.puanla(ad: String, puan: Int) {
        client.put(ApiYollari.kutuphaneGirisi(oyun)) {
            bearerAuth(t(ad))
            json(KutuphaneGirisi.serializer(), KutuphaneGirisi(puan = puan))
        }
    }

    private suspend fun ApiTestOrtami.kayitGir(ad: String): GunlukKaydi {
        saat.ilerlet(1.minutes)
        return client.post(ApiYollari.oyunGunlugu(oyun)) {
            bearerAuth(t(ad))
            json(GunlukKaydiIstegi.serializer(), GunlukKaydiIstegi(inceleme = "$ad yazdı"))
        }.govde(GunlukKaydi.serializer())
    }

    private suspend fun ApiTestOrtami.sil(ad: String, onay: String): HttpResponse =
        client.delete(ApiYollari.BEN) {
            bearerAuth(t(ad))
            json(HesapSilmeIstegi.serializer(), HesapSilmeIstegi(onay))
        }

    private suspend fun ApiTestOrtami.liste(ad: String, yol: String) =
        client.get(yol) { bearerAuth(t(ad)) }.govde(KullaniciListesi.serializer()).kullanicilar.map { it.kullaniciAdi }

    private suspend fun ApiTestOrtami.toplulukPuani(ad: String) =
        client.get("${ApiYollari.OYUNLAR}/$oyun") { bearerAuth(t(ad)) }.govde(OyunDetayi.serializer()).puanlar.toplulukPuani

    /** Şemadaki her UUID sütununda kullanıcının kimliğini arar; tablo eklendikçe test kendiliğinden kapsar. */
    private fun ApiTestOrtami.kalanSatirlar(kullaniciId: String): Map<String, Long> = transaction(veritabani) {
        val sutunlar = mutableListOf<Pair<String, String>>()
        exec("SELECT table_name, column_name FROM information_schema.columns WHERE table_schema = 'public' AND data_type = 'uuid'") { rs ->
            while (rs.next()) sutunlar += rs.getString(1) to rs.getString(2)
        }
        sutunlar.associate { (tablo, sutun) ->
            "$tablo.$sutun" to (exec("SELECT count(*) FROM \"$tablo\" WHERE \"$sutun\" = '${UUID.fromString(kullaniciId)}'") { rs -> rs.next(); rs.getLong(1) } ?: 0L)
        }.filterValues { it > 0 }
    }

    @Test
    fun hesapSilininceKullaniciyaAitHicbirSatirKalmaz() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        ayarlar.yaz(Ayar.ToplulukPuaniEsigi, 1)

        // mehmet'in uygulamadaki her türden izi.
        puanla("ayse", 8)
        puanla("mehmet", 4)
        val aysenin = kayitGir("ayse")
        val mehmetin = kayitGir("mehmet")
        client.put(ApiYollari.begeni(aysenin.id)) { bearerAuth(t("mehmet")) }
        client.put(ApiYollari.begeni(mehmetin.id)) { bearerAuth(t("ayse")) }
        client.post(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.TAKIP)) { bearerAuth(t("mehmet")) }
        client.post(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.ARKADASLIK)) { bearerAuth(t("zeynep")) }
        client.post(ApiYollari.arkadaslikIstegiKabul("zeynep")) { bearerAuth(t("mehmet")) }
        client.put(ApiYollari.kullaniciSekmesi("ayse", ApiYollari.SESSIZ)) { bearerAuth(t("mehmet")) }
        client.put(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.ENGEL)) { bearerAuth(t("ayse")) }
        client.delete(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.ENGEL)) { bearerAuth(t("ayse")) }
        client.post(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.TAKIP)) { bearerAuth(t("ayse")) }
        client.get(ApiYollari.DAVET_KODLARIM) { bearerAuth(t("mehmet")) }.govde(DavetKodlarimYaniti.serializer())
        client.put(ApiYollari.STEAM) {
            bearerAuth(t("mehmet"))
            json(SteamBaglaIstegi.serializer(), SteamBaglaIstegi("76561197960000002"))
        }
        client.put(ApiYollari.TELEFONUM) {
            bearerAuth(t("mehmet"))
            json(TelefonumIstegi.serializer(), TelefonumIstegi(TelefonNumarasi.hash("05321112233")!!))
        }
        client.put(ApiYollari.CIHAZ) {
            bearerAuth(t("mehmet"))
            json(CihazKaydiIstegi.serializer(), CihazKaydiIstegi(CihazPlatformu.ANDROID, "token-mehmet", "Europe/Istanbul"))
        }
        client.put(ApiYollari.BILDIRIM_TERCIHLERI) {
            bearerAuth(t("mehmet"))
            json(BildirimTercihleri.serializer(), BildirimTercihleri(mapOf(BildirimTuru.BEGENI to false)))
        }
        client.post(ApiYollari.SIKAYETLER) {
            bearerAuth(t("mehmet"))
            json(SikayetIstegi.serializer(), SikayetIstegi(SikayetSebebi.SPAM, kullaniciAdi = "zeynep"))
        }
        client.post(ApiYollari.SIKAYETLER) {
            bearerAuth(t("zeynep"))
            json(SikayetIstegi.serializer(), SikayetIstegi(SikayetSebebi.SPAM, gunlukKaydiId = mehmetin.id))
        }
        assertEquals(6.0, toplulukPuani("ayse"))
        assertTrue(kalanSatirlar(kimlikler.getValue("mehmet")).size > 10)

        assertEquals(HttpStatusCode.NoContent, sil("mehmet", " Mehmet ").status)

        assertEquals(emptyMap(), kalanSatirlar(kimlikler.getValue("mehmet")))
        // Topluluk puanı silinenin puanı olmadan yeniden hesaplanır.
        assertEquals(8.0, toplulukPuani("ayse"))
        // Başkalarının akışından, takipçi listelerinden ve beğenenler listelerinden kaybolur.
        assertEquals(emptyList(), liste("ayse", ApiYollari.kullaniciSekmesi("ayse", ApiYollari.SEKME_TAKIPCILER)))
        assertEquals(emptyList(), liste("ayse", ApiYollari.kullaniciSekmesi("ayse", ApiYollari.SEKME_TAKIP_EDILENLER)))
        assertEquals(emptyList(), liste("ayse", ApiYollari.begenenler(aysenin.id)))
        assertEquals(emptyList(), liste("zeynep", ApiYollari.kullaniciSekmesi("zeynep", ApiYollari.SEKME_ARKADASLAR)))
        val akis = client.get(ApiYollari.AKIS) { bearerAuth(t("zeynep")) }.govde(AkisSayfasi.serializer()).ogeler
        assertTrue(akis.none { it.kisi.kullaniciAdi == "mehmet" || it.arkadas?.kullaniciAdi == "mehmet" })
        assertEquals(HttpStatusCode.NotFound, client.get(ApiYollari.kullanici("mehmet")) { bearerAuth(t("ayse")) }.status)
        // Oturumu da silindi.
        assertEquals(HttpStatusCode.Unauthorized, client.get(ApiYollari.BEN) { bearerAuth(t("mehmet")) }.status)
    }

    @Test
    fun onaySizHesapSilinmez() = apiTesti {
        hazirla("ayse")

        assertEquals(HataKodu.HESAP_SILME_ONAYI_GECERSIZ, sil("ayse", "").govde(ApiHatasi.serializer()).kod)
        assertEquals(HttpStatusCode.BadRequest, sil("ayse", "evet").status)
        assertEquals(HttpStatusCode.BadRequest, sil("ayse", HesapSilme.PROFILSIZ_ONAY).status)
        // Onay gövdesi olmayan istek de reddedilir.
        assertNotEquals(HttpStatusCode.NoContent, client.delete(ApiYollari.BEN) { bearerAuth(t("ayse")) }.status)
        assertEquals(HttpStatusCode.OK, client.get(ApiYollari.BEN) { bearerAuth(t("ayse")) }.status)
    }

    @Test
    fun profiliOlmayanHesapSilKelimesiyleSilinir() = apiTesti {
        val giris = girisYap("yeni")
        tokenler["yeni"] = giris.oturumTokeni

        assertEquals(HttpStatusCode.BadRequest, sil("yeni", "yeni").status)
        assertEquals(HttpStatusCode.NoContent, sil("yeni", "SIL").status)
        assertEquals(emptyMap(), kalanSatirlar(giris.kullaniciId))
    }
}
