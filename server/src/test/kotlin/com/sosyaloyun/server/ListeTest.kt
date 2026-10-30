package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.GizlilikIstegi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.Liste
import com.sosyaloyun.shared.ListeGorunurlugu
import com.sosyaloyun.shared.ListeIstegi
import com.sosyaloyun.shared.ListeSayfasi
import com.sosyaloyun.shared.OyunAramaYaniti
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import org.jetbrains.exposed.v1.core.eq
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class ListeTest {
    private val tokenler = mutableMapOf<String, String>()
    private fun t(ad: String) = tokenler.getValue(ad)
    private val oyunlar = mutableMapOf<String, String>()

    private suspend fun ApiTestOrtami.hazirla(vararg adlar: String, faz: Int = 2) {
        ayarlar.yaz(Ayar.AktifFaz, faz)
        adlar.forEach { tokenler[it] = kayitliKullanici(it).oturumTokeni }
        listOf("Alfa", "Beta", "Gama").forEachIndexed { i, ad -> katalogKaynagi.oyunlar += KaynakOyun(i + 1L, "$ad Oyunu", KaynakTuru.ANA_OYUN) }
        katalogIceAktarici.tamIceAktar()
        listOf("Alfa", "Beta", "Gama").forEach { ad ->
            oyunlar[ad] = client.get(ApiYollari.OYUN_ARA) {
                bearerAuth(t(adlar.first()))
                parameter("q", ad)
            }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
        }
    }

    private suspend fun ApiTestOrtami.olustur(ad: String, istek: ListeIstegi): HttpResponse {
        saat.ilerlet(1.minutes)
        return client.post(ApiYollari.LISTELER) {
            bearerAuth(t(ad))
            json(ListeIstegi.serializer(), istek)
        }
    }

    private fun istek(baslik: String, vararg oyun: String, gorunurluk: ListeGorunurlugu = ListeGorunurlugu.HERKES) =
        ListeIstegi(baslik, "Açıklama", gorunurluk, oyun.map { oyunlar.getValue(it) })

    private suspend fun ApiTestOrtami.profilListeleri(izleyen: String, ad: String) =
        client.get(ApiYollari.kullaniciSekmesi(ad, ApiYollari.SEKME_LISTELER)) { bearerAuth(t(izleyen)) }.govde(ListeSayfasi.serializer()).listeler

    @Test
    fun listeOlusturulurSiralanirDuzenlenirSilinir() = apiTesti {
        hazirla("ayse", "mehmet")
        val liste = olustur("ayse", istek("En iyiler", "Gama", "Alfa")).govde(Liste.serializer())
        assertEquals(listOf("Gama Oyunu", "Alfa Oyunu"), liste.oyunlar.map { it.ad })
        assertTrue(liste.benim)

        val guncel = client.put(ApiYollari.liste(liste.id)) {
            bearerAuth(t("ayse"))
            json(ListeIstegi.serializer(), istek("  En en iyiler  ", "Alfa", "Beta", "Gama"))
        }.govde(Liste.serializer())
        assertEquals("En en iyiler", guncel.baslik)
        assertEquals(listOf("Alfa Oyunu", "Beta Oyunu", "Gama Oyunu"), guncel.oyunlar.map { it.ad })

        // Başkası düzenleyemez ve silemez.
        assertEquals(HttpStatusCode.NotFound, client.put(ApiYollari.liste(liste.id)) {
            bearerAuth(t("mehmet"))
            json(ListeIstegi.serializer(), istek("Ele geçirildi"))
        }.status)
        assertEquals(HttpStatusCode.NotFound, client.delete(ApiYollari.liste(liste.id)) { bearerAuth(t("mehmet")) }.status)

        val ozet = profilListeleri("mehmet", "ayse").single()
        assertEquals(3, ozet.oyunSayisi)
        assertEquals(3, ozet.onIzleme.size)

        assertEquals(HttpStatusCode.NoContent, client.delete(ApiYollari.liste(liste.id)) { bearerAuth(t("ayse")) }.status)
        assertEquals(emptyList(), profilListeleri("ayse", "ayse"))
    }

    @Test
    fun gecersizListeReddedilir() = apiTesti {
        hazirla("ayse")
        listOf(
            ListeIstegi("   "),
            ListeIstegi("x".repeat(101)),
            ListeIstegi("Tekrarlı", oyunIdleri = listOf(oyunlar.getValue("Alfa"), oyunlar.getValue("Alfa"))),
            ListeIstegi("Olmayan oyun", oyunIdleri = listOf("00000000-0000-0000-0000-000000000000")),
        ).forEach {
            assertEquals(HataKodu.LISTE_GECERSIZ, olustur("ayse", it).govde(ApiHatasi.serializer()).kod)
        }
    }

    @Test
    fun yalnizcaBanaListeBaskasinaVePaylasimaDonmez() = apiTesti {
        hazirla("ayse", "mehmet")
        val ozel = olustur("ayse", istek("Gizli plan", "Alfa", gorunurluk = ListeGorunurlugu.BEN)).govde(Liste.serializer())
        val acik = olustur("ayse", istek("Herkese", "Alfa")).govde(Liste.serializer())

        assertEquals(HttpStatusCode.NotFound, client.get(ApiYollari.liste(ozel.id)) { bearerAuth(t("mehmet")) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get(ApiYollari.paylasilanListe(ozel.id)).status)
        assertEquals(listOf("Herkese"), profilListeleri("mehmet", "ayse").map { it.baslik })
        assertEquals(setOf("Gizli plan", "Herkese"), profilListeleri("ayse", "ayse").map { it.baslik }.toSet())
        assertEquals("Herkese", client.get(ApiYollari.paylasilanListe(acik.id)).govde(Liste.serializer()).baslik)
        // Banlı hesabın listesi paylaşılmaz.
        transaction(veritabani) {
            KullaniciTablosu.update({ KullaniciTablosu.kullaniciAdi eq "ayse" }) { it[KullaniciTablosu.banli] = true }
        }
        assertEquals(HttpStatusCode.NotFound, client.get(ApiYollari.paylasilanListe(acik.id)).status)

        // Oyun detayındaki listeler yalnızca herkese açık olanları gösterir.
        val oyunun = client.get(ApiYollari.oyunListeleri(oyunlar.getValue("Alfa"))) { bearerAuth(t("mehmet")) }.govde(ListeSayfasi.serializer())
        assertEquals(listOf("Herkese"), oyunun.listeler.map { it.baslik })
    }

    @Test
    fun engelVeGizliProfilListeleriSaklar() = apiTesti {
        hazirla("ayse", "mehmet", "zeynep")
        val liste = olustur("ayse", istek("Ayşe'nin listesi", "Beta")).govde(Liste.serializer())
        client.put(ApiYollari.kullaniciSekmesi("mehmet", ApiYollari.ENGEL)) { bearerAuth(t("ayse")) }
        assertEquals(HttpStatusCode.NotFound, client.get(ApiYollari.liste(liste.id)) { bearerAuth(t("mehmet")) }.status)
        assertEquals(emptyList(), client.get(ApiYollari.oyunListeleri(oyunlar.getValue("Beta"))) { bearerAuth(t("mehmet")) }.govde(ListeSayfasi.serializer()).listeler)

        client.put(ApiYollari.GIZLILIK) {
            bearerAuth(t("ayse"))
            json(GizlilikIstegi.serializer(), GizlilikIstegi(true))
        }
        assertEquals(emptyList(), profilListeleri("zeynep", "ayse"))
        assertEquals(HttpStatusCode.NotFound, client.get(ApiYollari.paylasilanListe(liste.id)).status)
    }

    @Test
    fun listeBegenilirVeGeriAlinir() = apiTesti {
        hazirla("ayse", "mehmet")
        val liste = olustur("ayse", istek("Beğenilecek", "Alfa")).govde(Liste.serializer())
        client.put(ApiYollari.listeBegenisi(liste.id)) { bearerAuth(t("mehmet")) }
        val begenilmis = client.get(ApiYollari.liste(liste.id)) { bearerAuth(t("mehmet")) }.govde(Liste.serializer())
        assertEquals(1 to true, begenilmis.begeniSayisi to begenilmis.begendim)
        client.delete(ApiYollari.listeBegenisi(liste.id)) { bearerAuth(t("mehmet")) }
        assertEquals(0, client.get(ApiYollari.liste(liste.id)) { bearerAuth(t("ayse")) }.govde(Liste.serializer()).begeniSayisi)
    }

    @Test
    fun faz1deListelerKapali() = apiTesti {
        hazirla("ayse", faz = 1)
        assertEquals(HataKodu.OZELLIK_KAPALI, olustur("ayse", istek("Kapalı", "Alfa")).govde(ApiHatasi.serializer()).kod)
        assertEquals(HttpStatusCode.NotFound, client.get(ApiYollari.PROFILIM_LISTELER) { bearerAuth(t("ayse")) }.status)
    }
}
