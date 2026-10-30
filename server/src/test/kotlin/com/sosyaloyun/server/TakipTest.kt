package com.sosyaloyun.server

import com.sosyaloyun.server.test.ApiTestOrtami
import com.sosyaloyun.server.test.apiTesti
import com.sosyaloyun.server.test.govde
import com.sosyaloyun.server.test.json
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.DavetKodlarimYaniti
import com.sosyaloyun.shared.DavetKoduKullanIstegi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KullaniciListesi
import com.sosyaloyun.shared.ProfilIstegi
import com.sosyaloyun.shared.ProfilSayfasi
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TakipTest {
    private suspend fun ApiTestOrtami.takipEt(token: String, ad: String) =
        client.post(ApiYollari.kullaniciSekmesi(ad, ApiYollari.TAKIP)) { bearerAuth(token) }

    private suspend fun ApiTestOrtami.birak(token: String, ad: String) =
        client.delete(ApiYollari.kullaniciSekmesi(ad, ApiYollari.TAKIP)) { bearerAuth(token) }

    private suspend fun ApiTestOrtami.profil(token: String, ad: String): ProfilSayfasi =
        client.get(ApiYollari.kullanici(ad)) { bearerAuth(token) }.govde(ProfilSayfasi.serializer())

    private suspend fun ApiTestOrtami.liste(token: String, ad: String, sekme: String): List<String> =
        client.get(ApiYollari.kullaniciSekmesi(ad, sekme)) { bearerAuth(token) }
            .govde(KullaniciListesi.serializer()).kullanicilar.map { it.kullaniciAdi }

    @Test
    fun takipEdilirVeBirakilirSayaclarGuncellenir() = apiTesti {
        val ayse = kayitliKullanici("ayse").oturumTokeni
        val mehmet = kayitliKullanici("mehmet").oturumTokeni

        assertEquals(HttpStatusCode.NoContent, takipEt(ayse, "mehmet").status)

        val mehmetProfili = profil(ayse, "mehmet")
        assertEquals(1, mehmetProfili.takipciSayisi)
        assertEquals(0, mehmetProfili.takipEdilenSayisi)
        assertEquals(true, mehmetProfili.takipEdiyorum)
        assertEquals(1, client.get(ApiYollari.PROFILIM) { bearerAuth(ayse) }.govde(ProfilSayfasi.serializer()).takipEdilenSayisi)
        assertNull(client.get(ApiYollari.PROFILIM) { bearerAuth(ayse) }.govde(ProfilSayfasi.serializer()).takipEdiyorum)
        assertEquals(false, profil(mehmet, "ayse").takipEdiyorum)

        assertEquals(HttpStatusCode.NoContent, birak(ayse, "mehmet").status)
        assertEquals(0, profil(ayse, "mehmet").takipciSayisi)
        assertEquals(false, profil(ayse, "mehmet").takipEdiyorum)
    }

    @Test
    fun ikinciKezTakipEtmekTekTakipBirakir() = apiTesti {
        val ayse = kayitliKullanici("ayse").oturumTokeni
        kayitliKullanici("mehmet")

        takipEt(ayse, "mehmet")
        assertEquals(HttpStatusCode.NoContent, takipEt(ayse, "mehmet").status)

        assertEquals(1, profil(ayse, "mehmet").takipciSayisi)
    }

    @Test
    fun takipciVeTakipEdilenListeleri() = apiTesti {
        val ayse = kayitliKullanici("ayse").oturumTokeni
        val mehmet = kayitliKullanici("mehmet").oturumTokeni
        kayitliKullanici("zeynep")
        takipEt(ayse, "zeynep")
        takipEt(mehmet, "zeynep")
        takipEt(ayse, "mehmet")

        assertEquals(setOf("ayse", "mehmet"), liste(ayse, "zeynep", ApiYollari.SEKME_TAKIPCILER).toSet())
        assertEquals(setOf("zeynep", "mehmet"), liste(mehmet, "ayse", ApiYollari.SEKME_TAKIP_EDILENLER).toSet())
        assertEquals(emptyList(), liste(ayse, "zeynep", ApiYollari.SEKME_TAKIP_EDILENLER))
    }

    @Test
    fun kendiniTakipEdemezsin() = apiTesti {
        val ayse = kayitliKullanici("ayse").oturumTokeni

        val yanit = takipEt(ayse, "ayse")

        assertEquals(HttpStatusCode.BadRequest, yanit.status)
        assertEquals(ApiHatasi(HataKodu.KENDINI_TAKIP_EDEMEZSIN), yanit.govde(ApiHatasi.serializer()))
    }

    @Test
    fun olmayanKullaniciBulunamaz() = apiTesti {
        val ayse = kayitliKullanici("ayse").oturumTokeni

        assertEquals(HttpStatusCode.NotFound, takipEt(ayse, "yokboyle").status)
        val profil = client.get(ApiYollari.kullanici("yokboyle")) { bearerAuth(ayse) }
        assertEquals(ApiHatasi(HataKodu.KULLANICI_BULUNAMADI), profil.govde(ApiHatasi.serializer()))
    }

    @Test
    fun davetKodunuKullananKoduVereniOtomatikTakipEder() = apiTesti {
        val ayse = kayitliKullanici("ayse").oturumTokeni
        val kod = client.get(ApiYollari.DAVET_KODLARIM) { bearerAuth(ayse) }.govde(DavetKodlarimYaniti.serializer()).kodlar.first().kod
        val yeni = girisYap("yeni").oturumTokeni
        client.post(ApiYollari.DAVET_KULLAN) {
            bearerAuth(yeni)
            json(DavetKoduKullanIstegi.serializer(), DavetKoduKullanIstegi(kod))
        }
        client.put(ApiYollari.PROFIL) {
            bearerAuth(yeni)
            json(ProfilIstegi.serializer(), ProfilIstegi("yeni", "Yeni", Avatar.ROBOT))
        }

        assertEquals(listOf("yeni"), liste(ayse, "ayse", ApiYollari.SEKME_TAKIPCILER))
        assertEquals(true, profil(yeni, "ayse").takipEdiyorum)
        assertEquals(false, profil(ayse, "yeni").takipEdiyorum)
    }

    @Test
    fun kullanicilarAdlarindanBulunur() = apiTesti {
        val ayse = kayitliKullanici("ayse").oturumTokeni
        val sule = kayitliKullanici("sule_k").oturumTokeni
        client.put(ApiYollari.PROFIL) {
            bearerAuth(sule)
            json(ProfilIstegi.serializer(), ProfilIstegi("sule_k", "Şule Kılıç", Avatar.KEDI))
        }
        kayitliKullanici("mehmet")
        davetliKullanici("profilsiz")

        suspend fun ara(q: String) = client.get(ApiYollari.KULLANICI_ARA) {
            bearerAuth(ayse)
            parameter("q", q)
        }.govde(KullaniciListesi.serializer()).kullanicilar.map { it.kullaniciAdi }

        assertEquals(listOf("sule_k"), ara("sule kilic"))
        assertEquals(listOf("sule_k"), ara("ŞULE"))
        assertEquals(listOf("mehmet"), ara("@meh"))
        assertEquals(emptyList(), ara("profilsiz"))
        assertEquals(emptyList(), ara("m"))
    }
}
