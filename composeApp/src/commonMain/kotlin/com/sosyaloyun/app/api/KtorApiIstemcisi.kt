package com.sosyaloyun.app.api

import com.sosyaloyun.app.oturum.OturumDeposu
import com.sosyaloyun.shared.KuratorDurumu
import com.sosyaloyun.shared.ListeSayfasi
import com.sosyaloyun.shared.PuanGecmisi
import com.sosyaloyun.shared.HaftalikPuanDegisimi
import com.sosyaloyun.shared.ItirazDurumu
import com.sosyaloyun.shared.Istatistikler
import com.sosyaloyun.shared.YillikOzet
import com.sosyaloyun.shared.ItirazIstegi
import com.sosyaloyun.shared.GirisItirazIstegi
import com.sosyaloyun.shared.ListeIstegi
import com.sosyaloyun.shared.Liste
import com.sosyaloyun.shared.GizlilikIstegi
import com.sosyaloyun.shared.YorumListesi
import com.sosyaloyun.shared.YorumIstegi
import com.sosyaloyun.shared.Yorum
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
import com.sosyaloyun.shared.GunlukKaydiDetayi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.GunlukSayfasi
import com.sosyaloyun.shared.KullaniciAdiMusaitlikYaniti
import com.sosyaloyun.shared.KullaniciListesi
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.KutuphaneSayfasi
import com.sosyaloyun.shared.OrtakOyunlar
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.IncelemeListesi
import com.sosyaloyun.shared.OyunDetayi
import com.sosyaloyun.shared.HesapSilmeIstegi
import com.sosyaloyun.shared.SikayetIstegi
import com.sosyaloyun.shared.OkunmamisBildirimSayisi
import com.sosyaloyun.shared.BildirimTercihleri
import com.sosyaloyun.shared.BildirimListesi
import com.sosyaloyun.shared.Tur
import com.sosyaloyun.shared.Kesif
import com.sosyaloyun.shared.TopluTakipIstegi
import com.sosyaloyun.shared.RehberEslestirmeIstegi
import com.sosyaloyun.shared.TelefonumIstegi
import com.sosyaloyun.shared.BulunanKisiler
import com.sosyaloyun.shared.BulunanKisi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.PopulerOyunlar
import com.sosyaloyun.shared.SteamBaglaIstegi
import com.sosyaloyun.shared.SteamIceAktarmaSonucu
import com.sosyaloyun.shared.SteamBaglantisi
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

    override suspend fun hesabiSil(onay: String) {
        istek {
            http.delete(ApiYollari.BEN) {
                contentType(ContentType.Application.Json)
                setBody(HesapSilmeIstegi(onay))
            }
        }
    }

    override suspend fun steamBaglantisi(): SteamBaglantisi? = try {
        istek { http.get(ApiYollari.STEAM).body<SteamBaglantisi>() }
    } catch (e: ApiHatasiIstisnasi) {
        if (e.kod == HataKodu.STEAM_BAGLI_DEGIL) null else throw e
    }

    override suspend fun steamBagla(girdi: String): SteamIceAktarmaSonucu = istek {
        http.put(ApiYollari.STEAM) {
            contentType(ContentType.Application.Json)
            setBody(SteamBaglaIstegi(girdi))
        }.body()
    }

    override suspend fun steamBaglantisiniKaldir() {
        istek { http.delete(ApiYollari.STEAM) }
    }

    override suspend fun populerOyunlar(): List<OyunOzeti> = istek { http.get(ApiYollari.POPULER_OYUNLAR).body<PopulerOyunlar>().oyunlar }

    override suspend fun onboardingiTamamla() {
        istek { http.post(ApiYollari.ONBOARDING_TAMAMLA) }
    }

    override suspend fun telefonumuKaydet(hash: String) {
        istek {
            http.put(ApiYollari.TELEFONUM) {
                contentType(ContentType.Application.Json)
                setBody(TelefonumIstegi(hash))
            }
        }
    }

    override suspend fun rehberdenBul(hashler: List<String>): List<BulunanKisi> = istek {
        http.post(ApiYollari.ARKADAS_BUL_REHBER) {
            contentType(ContentType.Application.Json)
            setBody(RehberEslestirmeIstegi(hashler))
        }.body<BulunanKisiler>().kisiler
    }

    override suspend fun steamdenBul(): List<BulunanKisi> = istek { http.get(ApiYollari.ARKADAS_BUL_STEAM).body<BulunanKisiler>().kisiler }

    override suspend fun topluTakip(kullaniciAdlari: List<String>) {
        istek {
            http.post(ApiYollari.TOPLU_TAKIP) {
                contentType(ContentType.Application.Json)
                setBody(TopluTakipIstegi(kullaniciAdlari))
            }
        }
    }

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

    override suspend fun kesif(): Kesif = istek { http.get(ApiYollari.KESIF).body() }

    override suspend fun sikayetEt(istek: SikayetIstegi) {
        istek {
            http.post(ApiYollari.SIKAYETLER) {
                contentType(ContentType.Application.Json)
                setBody(istek)
            }
        }
    }

    override suspend fun bildirimler(): BildirimListesi = istek { http.get(ApiYollari.BILDIRIMLER).body() }

    override suspend fun bildirimleriOkunduSay() {
        istek { http.post(ApiYollari.BILDIRIMLER_OKUNDU) }
    }

    override suspend fun okunmamisBildirimSayisi(): Int = istek { http.get(ApiYollari.OKUNMAMIS_BILDIRIM_SAYISI).body<OkunmamisBildirimSayisi>().sayi }

    override suspend fun bildirimTercihleri(): BildirimTercihleri = istek { http.get(ApiYollari.BILDIRIM_TERCIHLERI).body() }

    override suspend fun bildirimTercihleriniKaydet(tercihler: BildirimTercihleri): BildirimTercihleri = istek {
        http.put(ApiYollari.BILDIRIM_TERCIHLERI) {
            contentType(ContentType.Application.Json)
            setBody(tercihler)
        }.body()
    }

    override suspend fun oyunListesi(tur: Tur?, platform: String?, imlec: String?): OyunAramaYaniti = istek {
        http.get(ApiYollari.OYUN_LISTESI) {
            tur?.let { parameter("tur", it.name) }
            platform?.let { parameter("platform", it) }
            imlec?.let { parameter("imlec", it) }
        }.body()
    }

    override suspend fun oyunIncelemeleri(oyunId: String, imlec: String?): IncelemeListesi =
        istek { http.get(ApiYollari.oyunIncelemeleri(oyunId)) { imlec?.let { parameter("imlec", it) } }.body() }

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

    override suspend fun engelle(kullaniciAdi: String) {
        istek { http.put(ApiYollari.kullaniciSekmesi(kullaniciAdi, ApiYollari.ENGEL)) }
    }

    override suspend fun engeliKaldir(kullaniciAdi: String) {
        istek { http.delete(ApiYollari.kullaniciSekmesi(kullaniciAdi, ApiYollari.ENGEL)) }
    }

    override suspend fun sessizeAl(kullaniciAdi: String) {
        istek { http.put(ApiYollari.kullaniciSekmesi(kullaniciAdi, ApiYollari.SESSIZ)) }
    }

    override suspend fun sessizdenCikar(kullaniciAdi: String) {
        istek { http.delete(ApiYollari.kullaniciSekmesi(kullaniciAdi, ApiYollari.SESSIZ)) }
    }

    override suspend fun engelListesi(tur: EngelListesiTuru, imlec: String?): KullaniciListesi {
        val yol = when (tur) {
            EngelListesiTuru.ENGELLENENLER -> ApiYollari.ENGELLENENLER
            EngelListesiTuru.SESSIZE_ALINANLAR -> ApiYollari.SESSIZE_ALINANLAR
        }
        return istek { http.get(yol) { imlec?.let { parameter("imlec", it) } }.body() }
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

    override suspend fun gunlukKaydiDetayi(kayitId: String): GunlukKaydiDetayi = istek { http.get("${ApiYollari.GUNLUK}/$kayitId").body() }

    override suspend fun begen(kayitId: String) {
        istek { http.put(ApiYollari.begeni(kayitId)) }
    }

    override suspend fun begeniyiGeriAl(kayitId: String) {
        istek { http.delete(ApiYollari.begeni(kayitId)) }
    }

    override suspend fun begenenler(kayitId: String): List<KullaniciOzeti> =
        istek { http.get(ApiYollari.begenenler(kayitId)).body<KullaniciListesi>().kullanicilar }

    override suspend fun yorumlar(kayitId: String, imlec: String?): YorumListesi =
        istek { http.get(ApiYollari.yorumlar(kayitId)) { imlec?.let { parameter("imlec", it) } }.body() }

    override suspend fun yorumYaz(kayitId: String, metin: String): Yorum = istek {
        http.post(ApiYollari.yorumlar(kayitId)) {
            contentType(ContentType.Application.Json)
            setBody(YorumIstegi(metin))
        }.body()
    }

    override suspend fun yorumSil(yorumId: String) {
        istek { http.delete("${ApiYollari.YORUMLAR}/$yorumId") }
    }

    override suspend fun gizlilikAyarla(gizli: Boolean) {
        istek {
            http.put(ApiYollari.GIZLILIK) {
                contentType(ContentType.Application.Json)
                setBody(GizlilikIstegi(gizli))
            }
        }
    }

    /** Bütün sayfaları okur; bekleyen istekler ayarlarda tek listede gösterilir. */
    override suspend fun takipIstekleri(): List<KullaniciOzeti> = buildList {
        var imlec: String? = null
        do {
            val sayfa = istek { http.get(ApiYollari.TAKIP_ISTEKLERI) { imlec?.let { parameter("imlec", it) } }.body<KullaniciListesi>() }
            addAll(sayfa.kullanicilar)
            imlec = sayfa.sonrakiImlec
        } while (imlec != null)
    }

    override suspend fun takipIsteginiKabulEt(kullaniciAdi: String) {
        istek { http.post(ApiYollari.takipIstegiKabul(kullaniciAdi)) }
    }

    override suspend fun takipIsteginiReddet(kullaniciAdi: String) {
        istek { http.delete(ApiYollari.takipIstegi(kullaniciAdi)) }
    }

    override suspend fun profilListeleri(kullaniciAdi: String?, imlec: String?): ListeSayfasi = istek {
        val yol = kullaniciAdi?.let { ApiYollari.kullaniciSekmesi(it, ApiYollari.SEKME_LISTELER) } ?: ApiYollari.PROFILIM_LISTELER
        http.get(yol) { imlec?.let { parameter("imlec", it) } }.body()
    }

    override suspend fun liste(listeId: String): Liste = istek { http.get(ApiYollari.liste(listeId)).body() }

    override suspend fun listeOlustur(istek: ListeIstegi): Liste = istek {
        http.post(ApiYollari.LISTELER) {
            contentType(ContentType.Application.Json)
            setBody(istek)
        }.body()
    }

    override suspend fun listeGuncelle(listeId: String, istek: ListeIstegi): Liste = istek {
        http.put(ApiYollari.liste(listeId)) {
            contentType(ContentType.Application.Json)
            setBody(istek)
        }.body()
    }

    override suspend fun listeSil(listeId: String) {
        istek { http.delete(ApiYollari.liste(listeId)) }
    }

    override suspend fun listeBegen(listeId: String) {
        istek { http.put(ApiYollari.listeBegenisi(listeId)) }
    }

    override suspend fun listeBegenisiniGeriAl(listeId: String) {
        istek { http.delete(ApiYollari.listeBegenisi(listeId)) }
    }

    override suspend fun oyunListeleri(oyunId: String): ListeSayfasi = istek { http.get(ApiYollari.oyunListeleri(oyunId)).body() }

    override suspend fun kuratorDurumu(): KuratorDurumu = istek { http.get(ApiYollari.KURATOR).body() }

    override suspend fun kuratorBasvur(): KuratorDurumu = istek { http.post(ApiYollari.KURATOR_BASVURU).body() }

    override suspend fun kuratorListeleri(): ListeSayfasi = istek { http.get(ApiYollari.KURATOR_LISTELERI).body() }

    override suspend fun puanGecmisi(oyunId: String): PuanGecmisi = istek { http.get(ApiYollari.puanGecmisi(oyunId)).body() }

    override suspend fun puanDegisimi(): HaftalikPuanDegisimi = istek { http.get(ApiYollari.PUAN_DEGISIMI).body() }

    override suspend fun itirazDurumu(): ItirazDurumu = istek { http.get(ApiYollari.ITIRAZ).body() }

    override suspend fun istatistikler(kullaniciAdi: String?, yil: Int?): Istatistikler = istek {
        val yol = kullaniciAdi?.let { ApiYollari.kullaniciSekmesi(it, ApiYollari.SEKME_ISTATISTIK) } ?: ApiYollari.PROFILIM_ISTATISTIK
        http.get(yol) { yil?.let { parameter("yil", it) } }.body()
    }

    override suspend fun yillikOzet(kullaniciAdi: String?, yil: Int): YillikOzet = istek {
        val yol = kullaniciAdi?.let { ApiYollari.kullaniciSekmesi(it, ApiYollari.SEKME_YILLIK_OZET) } ?: ApiYollari.PROFILIM_YILLIK_OZET
        http.get(yol) { parameter("yil", yil) }.body()
    }

    override suspend fun itirazEt(metin: String): ItirazDurumu = istek {
        http.post(ApiYollari.ITIRAZ) {
            contentType(ContentType.Application.Json)
            setBody(ItirazIstegi(metin))
        }.body()
    }

    override suspend fun girisItirazi(anahtar: String, metin: String) {
        istek {
            http.post(ApiYollari.ITIRAZ_GIRIS) {
                contentType(ContentType.Application.Json)
                setBody(GirisItirazIstegi(anahtar, metin))
            }
        }
    }

    private fun AkisSekmesi.parametre() = if (this == AkisSekmesi.ARKADASLAR) "arkadaslar" else "takip"

    /** Backend'in `ApiHatasi` gövdesiyle döndüğü 4xx yanıtları [ApiHatasiIstisnasi]'na çevirir. */
    private suspend fun <T> istek(blok: suspend () -> T): T = try {
        blok()
    } catch (e: ClientRequestException) {
        val hata = runCatching { ApiJson.decodeFromString(ApiHatasi.serializer(), e.response.bodyAsText()) }.getOrNull()
        throw if (hata != null) ApiHatasiIstisnasi(hata.kod, hata.ayrinti, hata.itirazAnahtari) else e
    }
}

/** Derlemede bir adres verildiyse (ör. Railway test ortamı) o, verilmediyse yerel geliştirme server'ı. */
fun sunucuAdresi(): String = DerlemeAyarlari.SUNUCU_ADRESI.ifBlank { yerelSunucuAdresi() }

/** Geliştirme makinesinde çalışan server. */
expect fun yerelSunucuAdresi(): String
