package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Gorunurluk
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiDetayi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.PaylasilanProfil
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaInstant
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class PaylasimTest {
    private val tokenler = mutableMapOf<String, String>()
    private fun t(ad: String) = tokenler.getValue(ad)
    private lateinit var oyun: String

    private suspend fun ApiTestOrtami.hazirla(vararg adlar: String) {
        adlar.forEach { tokenler[it] = kayitliKullanici(it).oturumTokeni }
        katalogKaynagi.oyunlar += KaynakOyun(1, "Paylaşım Oyunu", KaynakTuru.ANA_OYUN)
        katalogIceAktarici.tamIceAktar()
        oyun = client.get(ApiYollari.OYUN_ARA) {
            bearerAuth(t(adlar.first()))
            parameter("q", "paylasim")
        }.govde(OyunAramaYaniti.serializer()).oyunlar.single().id
    }

    private suspend fun ApiTestOrtami.kayitGir(ad: String, gorunurluk: Gorunurluk): GunlukKaydi {
        saat.ilerlet(1.minutes)
        return client.post(ApiYollari.oyunGunlugu(oyun)) {
            bearerAuth(t(ad))
            json(GunlukKaydiIstegi.serializer(), GunlukKaydiIstegi(inceleme = "${gorunurluk.name} inceleme", gorunurluk = gorunurluk))
        }.govde(GunlukKaydi.serializer())
    }

    private suspend fun ApiTestOrtami.paylasilanKayit(id: String) = client.get(ApiYollari.paylasilanKayit(id))

    private suspend fun ApiTestOrtami.paylasilanProfil(ad: String) = client.get(ApiYollari.paylasilanProfil(ad))

    @Test
    fun yalnizcaHerkesGorunurlugundekiKayitlarOturumsuzDoner() = apiTesti {
        hazirla("ayse", "mehmet")
        val herkes = kayitGir("ayse", Gorunurluk.HERKES)
        val digerleri = listOf(Gorunurluk.TAKIPCILER, Gorunurluk.ARKADASLAR, Gorunurluk.BEN).map { kayitGir("ayse", it) }
        client.put(ApiYollari.begeni(herkes.id)) { bearerAuth(t("mehmet")) }

        val detay = paylasilanKayit(herkes.id).govde(GunlukKaydiDetayi.serializer())
        assertEquals("HERKES inceleme", detay.kayit.inceleme)
        assertEquals("ayse", detay.sahip.kullaniciAdi)
        assertEquals("Paylaşım Oyunu", detay.oyun.ad)
        assertEquals(1, detay.kayit.begeniSayisi)
        assertEquals(false, detay.kayit.begendim)
        digerleri.forEach { assertEquals(HttpStatusCode.NotFound, paylasilanKayit(it.id).status) }
        assertEquals(HttpStatusCode.NotFound, paylasilanKayit("olmayan").status)

        val profil = paylasilanProfil("ayse").govde(PaylasilanProfil.serializer())
        assertEquals("ayse", profil.profil.kullanici.kullaniciAdi)
        assertEquals(listOf(herkes.id), profil.sonKayitlar.map { it.kayit.id })
        // İzleyen olmadığından ilişki alanları boş.
        assertNull(profil.profil.takipEdiyorum)
        assertNull(profil.profil.arkadaslik)
        assertEquals(HttpStatusCode.NotFound, paylasilanProfil("olmayan").status)
    }

    @Test
    fun banliVeAskidakiHesabinIcerigiPaylasilmaz() = apiTesti {
        hazirla("ayse", "mehmet")
        val aysenin = kayitGir("ayse", Gorunurluk.HERKES)
        val mehmetin = kayitGir("mehmet", Gorunurluk.HERKES)

        transaction(veritabani) {
            KullaniciTablosu.update({ KullaniciTablosu.kullaniciAdi eq "ayse" }) { it[banli] = true }
            KullaniciTablosu.update({ KullaniciTablosu.kullaniciAdi eq "mehmet" }) {
                it[askininBitisi] = (saat.now() + 7.days).toJavaInstant().atOffset(ZoneOffset.UTC)
            }
        }
        assertEquals(HttpStatusCode.NotFound, paylasilanKayit(aysenin.id).status)
        assertEquals(HttpStatusCode.NotFound, paylasilanProfil("ayse").status)
        assertEquals(HttpStatusCode.NotFound, paylasilanKayit(mehmetin.id).status)
        assertEquals(HttpStatusCode.NotFound, paylasilanProfil("mehmet").status)

        // Askı bitince içerik yeniden paylaşılabilir.
        saat.ilerlet(8.days)
        assertEquals(HttpStatusCode.OK, paylasilanKayit(mehmetin.id).status)
    }

    @Test
    fun webSitesiAyniAdrestenSunulurVeIgdbAtfiVar() = apiTesti {
        val tanitim = client.get("/index.html")
        assertEquals(HttpStatusCode.OK, tanitim.status)
        assertTrue("IGDB" in tanitim.bodyAsText())
        assertEquals(HttpStatusCode.OK, client.get("/").status)
        assertEquals(HttpStatusCode.OK, client.get("/kayit.html").status)
        assertEquals(HttpStatusCode.OK, client.get("/profil.html").status)
    }
}
