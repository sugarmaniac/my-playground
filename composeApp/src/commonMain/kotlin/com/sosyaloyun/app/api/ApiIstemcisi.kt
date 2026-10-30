package com.sosyaloyun.app.api

import com.sosyaloyun.shared.AkisSayfasi
import com.sosyaloyun.shared.AkisSekmesi
import com.sosyaloyun.shared.BenYaniti
import com.sosyaloyun.shared.DavetKodu
import com.sosyaloyun.shared.GirisYaniti
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiDetayi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.GunlukSayfasi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.KimlikSaglayici
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
import com.sosyaloyun.shared.SaglikYaniti

/** Mobil uygulamanın backend'e açılan tek kapısı; ViewModel testleri bunun sahtesiyle çalışır. */
interface ApiIstemcisi {
    suspend fun saglik(): SaglikYaniti

    suspend fun girisYap(saglayici: KimlikSaglayici, kimlikTokeni: String): GirisYaniti

    suspend fun cikisYap()

    suspend fun ben(): BenYaniti

    suspend fun davetKoduKullan(kod: String)

    suspend fun davetKodlarim(): List<DavetKodu>

    suspend fun kullaniciAdiMusaitlik(ad: String): KullaniciAdiMusaitlikYaniti

    suspend fun profilKaydet(istek: ProfilIstegi): BenYaniti

    suspend fun oyunAra(sorgu: String, imlec: String? = null): OyunAramaYaniti

    suspend fun oyunDetayi(oyunId: String): OyunDetayi

    /** Boş giriş kütüphaneden kaldırır. */
    suspend fun kutuphaneGirisiKaydet(oyunId: String, giris: KutuphaneGirisi): KutuphaneGirisi

    suspend fun gunlukKaydiGir(oyunId: String, istek: GunlukKaydiIstegi): GunlukKaydi

    suspend fun gunlukKaydiDuzenle(kayitId: String, istek: GunlukKaydiIstegi): GunlukKaydi

    suspend fun gunlukKaydiSil(kayitId: String)

    /** [kullaniciAdi] null ise kendi profilin. */
    suspend fun profil(kullaniciAdi: String?): ProfilSayfasi

    /** Günlük ya da yalnızca incelemeli kayıtlar; başkasınınsa görme yetkisi olanlar. */
    suspend fun profilGunlugu(kullaniciAdi: String?, yalnizcaIncelemeler: Boolean, imlec: String? = null): GunlukSayfasi

    /** Oyunlar sekmesi ([durum] bir Durum adı ya da "durumsuz") ya da istek listesi. */
    suspend fun profilKutuphanesi(kullaniciAdi: String?, istekListesi: Boolean, durum: String? = null, imlec: String? = null): KutuphaneSayfasi

    suspend fun takipEt(kullaniciAdi: String)

    suspend fun takibiBirak(kullaniciAdi: String)

    suspend fun kisiListesi(kullaniciAdi: String, tur: KisiListesiTuru, imlec: String? = null): KullaniciListesi

    /** İstek gönderir; karşı taraf istek göndermişse arkadaş olunur. */
    suspend fun arkadaslikIste(kullaniciAdi: String)

    /** Arkadaşlığı bitirir ya da gönderilmiş isteği geri alır. */
    suspend fun arkadasligiBitir(kullaniciAdi: String)

    suspend fun arkadaslikKabulEt(kullaniciAdi: String)

    suspend fun arkadaslikReddet(kullaniciAdi: String)

    suspend fun gelenArkadaslikIstekleri(imlec: String? = null): KullaniciListesi

    suspend fun ortakOyunlar(kullaniciAdi: String): OrtakOyunlar

    suspend fun akis(sekme: AkisSekmesi, imlec: String? = null): AkisSayfasi

    /** [sonra] imlecinden daha yeni olay sayısı ("yeni gönderiler" uyarısı). */
    suspend fun akisYeniSayisi(sekme: AkisSekmesi, sonra: String?): Int

    suspend fun akisOnerileri(): List<KullaniciOzeti>

    /** Diğer işaretlere dokunmadan istek listesine ekler. */
    suspend fun istekListesineEkle(oyunId: String)

    suspend fun gunlukKaydiDetayi(kayitId: String): GunlukKaydiDetayi

    suspend fun begen(kayitId: String)

    suspend fun begeniyiGeriAl(kayitId: String)

    suspend fun begenenler(kayitId: String): List<KullaniciOzeti>

    suspend fun kullaniciAra(sorgu: String, imlec: String? = null): KullaniciListesi

    suspend fun vitrinKaydet(oyunIdleri: List<String>): ProfilSayfasi
}

enum class KisiListesiTuru { TAKIPCILER, TAKIP_EDILENLER, ARKADASLAR }

/** Backend'in `ApiHatasi` gövdesiyle reddettiği istek. Bağlantı hataları bu değil, başka istisnalardır. */
class ApiHatasiIstisnasi(val kod: HataKodu) : Exception(kod.name)
