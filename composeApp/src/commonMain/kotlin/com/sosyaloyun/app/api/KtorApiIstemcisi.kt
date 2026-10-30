package com.sosyaloyun.app.api

import com.sosyaloyun.app.oturum.OturumDeposu
import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.ApiJson
import com.sosyaloyun.shared.ApiYollari
import com.sosyaloyun.shared.AkisSayfasi
import com.sosyaloyun.shared.AkisSekmesi
import com.sosyaloyun.shared.BenYaniti
import com.sosyaloyun.shared.YeniOlaySayisi
import com.sosyaloyun.shared.DavetKodlarimYaniti
import com.sosyaloyun.shared.DavetKodu
import com.sosyaloyun.shared.DavetKoduKullanIstegi
import com.sosyaloyun.shared.GirisIstegi
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.GunlukSayfasi
import com.sosyaloyun.shared.KullaniciAdiMusaitlikYaniti
import com.sosyaloyun.shared.KullaniciListesi
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.KutuphaneSayfasi
import com.sosyaloyun.shared.OrtakOyunlar
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.OyunDetayi
import com.sosyaloyun.shared.ProfilIstegi
import com.sosyaloyun.shared.ProfilSayfasi
import com.sosyaloyun.shared.VitrinIstegi
import com.sosyaloyun.shared.GirisYaniti
import com.sosyaloyun.shared.KimlikSaglayici
import com.sosyaloyun.shared.SaglikYaniti
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json

class KtorApiIstemcisi(
    private val oturumDeposu: OturumDeposu,
    sunucuAdresi: String = sunucuAdresi(),
) : ApiIstemcisi {
    private val http = HttpClient {
        expectSuccess = true
        install(ContentNegotiation) { json(ApiJson) }
        defaultRequest {
            url(sunucuAdresi)
            oturumDeposu.oku()?.let { bearerAuth(it) }
        }
    }

    override suspend fun saglik(): SaglikYaniti = istek { http.get(ApiYollari.SAGLIK).body() }

    override suspend fun girisYap(saglayici: KimlikSaglayici, kimlikTokeni: String): GirisYaniti = istek {
        http.post(ApiYollari.OTURUM) {
            contentType(ContentType.Application.Json)
            setBody(GirisIstegi(saglayici, kimlikTokeni))
        }.body()
    }

    override suspend fun cikisYap() {
        istek { http.delete(ApiYollari.OTURUM) }
    }

    override suspend fun ben(): BenYaniti = istek { http.get(ApiYollari.BEN).body() }

    override suspend fun davetKoduKullan(kod: String) {
        istek {
            http.post(ApiYollari.DAVET_KULLAN) {
                contentType(ContentType.Application.Json)
                setBody(DavetKoduKullanIstegi(kod))
            }
        }
    }

    override suspend fun davetKodlarim(): List<DavetKodu> =
        istek { http.get(ApiYollari.DAVET_KODLARIM).body<DavetKodlarimYaniti>().kodlar }

    override suspend fun kullaniciAdiMusaitlik(ad: String): KullaniciAdiMusaitlikYaniti = istek {
        http.get(ApiYollari.KULLANICI_ADI_MUSAITLIK) { parameter("ad", ad) }.body()
    }

    override suspend fun profilKaydet(istek: ProfilIstegi): BenYaniti = istek {
        http.put(ApiYollari.PROFIL) {
            contentType(ContentType.Application.Json)
            setBody(istek)
        }.body()
    }

    override suspend fun oyunAra(sorgu: String, imlec: String?): OyunAramaYaniti = istek {
        http.get(ApiYollari.OYUN_ARA) {
            parameter("q", sorgu)
            imlec?.let { parameter("imlec", it) }
        }.body()
    }

    override suspend fun oyunDetayi(oyunId: String): OyunDetayi = istek { http.get("${ApiYollari.OYUNLAR}/$oyunId").body() }

    override suspend fun kutuphaneGirisiKaydet(oyunId: String, giris: KutuphaneGirisi): KutuphaneGirisi = istek {
        http.put(ApiYollari.kutuphaneGirisi(oyunId)) {
            contentType(ContentType.Application.Json)
            setBody(giris)
        }.body()
    }

    override suspend fun gunlukKaydiGir(oyunId: String, istek: GunlukKaydiIstegi): GunlukKaydi = istek {
        http.post(ApiYollari.oyunGunlugu(oyunId)) {
            contentType(ContentType.Application.Json)
            setBody(istek)
        }.body()
    }

    override suspend fun gunlukKaydiDuzenle(kayitId: String, istek: GunlukKaydiIstegi): GunlukKaydi = istek {
        http.put("${ApiYollari.GUNLUK}/$kayitId") {
            contentType(ContentType.Application.Json)
            setBody(istek)
        }.body()
    }

    override suspend fun gunlukKaydiSil(kayitId: String) {
        istek { http.delete("${ApiYollari.GUNLUK}/$kayitId") }
    }

    override suspend fun profil(kullaniciAdi: String?): ProfilSayfasi =
        istek { http.get(kullaniciAdi?.let(ApiYollari::kullanici) ?: ApiYollari.PROFILIM).body() }

    override suspend fun profilGunlugu(kullaniciAdi: String?, yalnizcaIncelemeler: Boolean, imlec: String?): GunlukSayfasi = istek {
        val yol = when {
            kullaniciAdi != null -> ApiYollari.kullaniciSekmesi(kullaniciAdi, if (yalnizcaIncelemeler) ApiYollari.SEKME_INCELEMELER else ApiYollari.SEKME_GUNLUK)
            yalnizcaIncelemeler -> ApiYollari.PROFILIM_INCELEMELER
            else -> ApiYollari.PROFILIM_GUNLUK
        }
        http.get(yol) { imlec?.let { parameter("imlec", it) } }.body()
    }

    override suspend fun profilKutuphanesi(kullaniciAdi: String?, istekListesi: Boolean, durum: String?, imlec: String?): KutuphaneSayfasi = istek {
        val yol = when {
            kullaniciAdi != null -> ApiYollari.kullaniciSekmesi(kullaniciAdi, if (istekListesi) ApiYollari.SEKME_ISTEK_LISTESI else ApiYollari.SEKME_OYUNLAR)
            istekListesi -> ApiYollari.PROFILIM_ISTEK_LISTESI
            else -> ApiYollari.PROFILIM_OYUNLAR
        }
        http.get(yol) {
            durum?.let { parameter("durum", it) }
            imlec?.let { parameter("imlec", it) }
        }.body()
    }

    override suspend fun takipEt(kullaniciAdi: String) {
        istek { http.post(ApiYollari.kullaniciSekmesi(kullaniciAdi, ApiYollari.TAKIP)) }
    }

    override suspend fun takibiBirak(kullaniciAdi: String) {
        istek { http.delete(ApiYollari.kullaniciSekmesi(kullaniciAdi, ApiYollari.TAKIP)) }
    }

    override suspend fun kisiListesi(kullaniciAdi: String, tur: KisiListesiTuru, imlec: String?): KullaniciListesi = istek {
        val sekme = when (tur) {
            KisiListesiTuru.TAKIPCILER -> ApiYollari.SEKME_TAKIPCILER
            KisiListesiTuru.TAKIP_EDILENLER -> ApiYollari.SEKME_TAKIP_EDILENLER
            KisiListesiTuru.ARKADASLAR -> ApiYollari.SEKME_ARKADASLAR
        }
        http.get(ApiYollari.kullaniciSekmesi(kullaniciAdi, sekme)) { imlec?.let { parameter("imlec", it) } }.body()
    }

    override suspend fun arkadaslikIste(kullaniciAdi: String) {
        istek { http.post(ApiYollari.kullaniciSekmesi(kullaniciAdi, ApiYollari.ARKADASLIK)) }
    }

    override suspend fun arkadasligiBitir(kullaniciAdi: String) {
        istek { http.delete(ApiYollari.kullaniciSekmesi(kullaniciAdi, ApiYollari.ARKADASLIK)) }
    }

    override suspend fun arkadaslikKabulEt(kullaniciAdi: String) {
        istek { http.post(ApiYollari.arkadaslikIstegiKabul(kullaniciAdi)) }
    }

    override suspend fun arkadaslikReddet(kullaniciAdi: String) {
        istek { http.delete(ApiYollari.arkadaslikIstegi(kullaniciAdi)) }
    }

    override suspend fun gelenArkadaslikIstekleri(imlec: String?): KullaniciListesi = istek {
        http.get(ApiYollari.ARKADASLIK_ISTEKLERI) { imlec?.let { parameter("imlec", it) } }.body()
    }

    override suspend fun ortakOyunlar(kullaniciAdi: String): OrtakOyunlar =
        istek { http.get(ApiYollari.kullaniciSekmesi(kullaniciAdi, ApiYollari.SEKME_ORTAK)).body() }

    override suspend fun kullaniciAra(sorgu: String, imlec: String?): KullaniciListesi = istek {
        http.get(ApiYollari.KULLANICI_ARA) {
            parameter("q", sorgu)
            imlec?.let { parameter("imlec", it) }
        }.body()
    }

    override suspend fun vitrinKaydet(oyunIdleri: List<String>): ProfilSayfasi = istek {
        http.put(ApiYollari.PROFILIM_VITRIN) {
            contentType(ContentType.Application.Json)
            setBody(VitrinIstegi(oyunIdleri))
        }.body()
    }

    override suspend fun akis(sekme: AkisSekmesi, imlec: String?): AkisSayfasi = istek {
        http.get(ApiYollari.AKIS) {
            parameter("sekme", sekme.parametre())
            imlec?.let { parameter("imlec", it) }
        }.body()
    }

    override suspend fun akisYeniSayisi(sekme: AkisSekmesi, sonra: String?): Int = istek {
        http.get(ApiYollari.AKIS_YENI) {
            parameter("sekme", sekme.parametre())
            sonra?.let { parameter("sonra", it) }
        }.body<YeniOlaySayisi>().sayi
    }

    override suspend fun akisOnerileri(): List<KullaniciOzeti> = istek { http.get(ApiYollari.AKIS_ONERILER).body<KullaniciListesi>().kullanicilar }

    override suspend fun istekListesineEkle(oyunId: String) {
        istek { http.post(ApiYollari.istekListesi(oyunId)) }
    }

    private fun AkisSekmesi.parametre() = if (this == AkisSekmesi.ARKADASLAR) "arkadaslar" else "takip"

    /** Backend'in `ApiHatasi` gövdesiyle döndüğü 4xx yanıtları [ApiHatasiIstisnasi]'na çevirir. */
    private suspend fun <T> istek(blok: suspend () -> T): T = try {
        blok()
    } catch (e: ClientRequestException) {
        val hata = runCatching { ApiJson.decodeFromString(ApiHatasi.serializer(), e.response.bodyAsText()) }.getOrNull()
        throw if (hata != null) ApiHatasiIstisnasi(hata.kod) else e
    }
}

/** Derlemede bir adres verildiyse (ör. Railway test ortamı) o, verilmediyse yerel geliştirme server'ı. */
fun sunucuAdresi(): String = DerlemeAyarlari.SUNUCU_ADRESI.ifBlank { yerelSunucuAdresi() }

/** Geliştirme makinesinde çalışan server. */
expect fun yerelSunucuAdresi(): String
