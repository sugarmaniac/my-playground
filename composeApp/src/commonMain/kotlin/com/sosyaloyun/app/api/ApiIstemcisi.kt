package com.sosyaloyun.app.api

import com.sosyaloyun.shared.ListeSayfasi
import com.sosyaloyun.shared.ListeIstegi
import com.sosyaloyun.shared.Liste
import com.sosyaloyun.shared.YorumListesi
import com.sosyaloyun.shared.Yorum
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
import com.sosyaloyun.shared.BulunanKisi
import com.sosyaloyun.shared.BildirimListesi
import com.sosyaloyun.shared.BildirimTercihleri
import com.sosyaloyun.shared.IncelemeListesi
import com.sosyaloyun.shared.SikayetIstegi
import com.sosyaloyun.shared.Kesif
import com.sosyaloyun.shared.Tur
import com.sosyaloyun.shared.SteamBaglantisi
import com.sosyaloyun.shared.SteamIceAktarmaSonucu
import com.sosyaloyun.shared.OyunDetayi
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.ProfilIstegi
import com.sosyaloyun.shared.ProfilSayfasi
import com.sosyaloyun.shared.SaglikYaniti

/** Mobil uygulamanın backend'e açılan tek kapısı; ViewModel testleri bunun sahtesiyle çalışır. */
interface ApiIstemcisi {
    suspend fun saglik(): SaglikYaniti

    suspend fun girisYap(saglayici: KimlikSaglayici, kimlikTokeni: String): GirisYaniti

    suspend fun cikisYap()

    suspend fun ben(): BenYaniti

    /** Hesabı kalıcı olarak siler; [onay] kullanıcının elle yazdığı kullanıcı adı. */
    suspend fun hesabiSil(onay: String)

    /** Bağlı değilse null. */
    suspend fun steamBaglantisi(): SteamBaglantisi?

    suspend fun steamBagla(girdi: String): SteamIceAktarmaSonucu

    suspend fun steamBaglantisiniKaldir()

    suspend fun populerOyunlar(): List<OyunOzeti>

    suspend fun onboardingiTamamla()

    /** Kendi numaranın cihazda üretilmiş hash'i; arkadaşların seni rehberden bulur. */
    suspend fun telefonumuKaydet(hash: String)

    suspend fun rehberdenBul(hashler: List<String>): List<BulunanKisi>

    suspend fun steamdenBul(): List<BulunanKisi>

    suspend fun topluTakip(kullaniciAdlari: List<String>)

    suspend fun davetKoduKullan(kod: String)

    suspend fun davetKodlarim(): List<DavetKodu>

    suspend fun kullaniciAdiMusaitlik(ad: String): KullaniciAdiMusaitlikYaniti

    suspend fun profilKaydet(istek: ProfilIstegi): BenYaniti

    suspend fun oyunAra(sorgu: String, imlec: String? = null): OyunAramaYaniti

    suspend fun oyunDetayi(oyunId: String): OyunDetayi

    suspend fun kesif(): Kesif

    suspend fun sikayetEt(istek: SikayetIstegi)

    suspend fun bildirimler(): BildirimListesi

    suspend fun bildirimleriOkunduSay()

    suspend fun okunmamisBildirimSayisi(): Int

    suspend fun bildirimTercihleri(): BildirimTercihleri

    suspend fun bildirimTercihleriniKaydet(tercihler: BildirimTercihleri): BildirimTercihleri

    /** Bir türdeki ya da platformdaki oyunlar, popüler önce. */
    suspend fun oyunListesi(tur: Tur?, platform: String?, imlec: String? = null): OyunAramaYaniti

    suspend fun oyunIncelemeleri(oyunId: String, imlec: String? = null): IncelemeListesi

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

    suspend fun engelle(kullaniciAdi: String)

    suspend fun engeliKaldir(kullaniciAdi: String)

    suspend fun sessizeAl(kullaniciAdi: String)

    suspend fun sessizdenCikar(kullaniciAdi: String)

    suspend fun engelListesi(tur: EngelListesiTuru, imlec: String? = null): KullaniciListesi

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

    /** Faz 2 (#47): kaydın yorumları, eskiden yeniye. */
    suspend fun yorumlar(kayitId: String, imlec: String? = null): YorumListesi

    suspend fun yorumYaz(kayitId: String, metin: String): Yorum

    suspend fun yorumSil(yorumId: String)

    /** Faz 2 (#48): gizli profil ve gelen takip istekleri. */
    suspend fun gizlilikAyarla(gizli: Boolean)

    suspend fun takipIstekleri(): List<KullaniciOzeti>

    suspend fun takipIsteginiKabulEt(kullaniciAdi: String)

    suspend fun takipIsteginiReddet(kullaniciAdi: String)

    /** Faz 2 (#49): listeler. [kullaniciAdi] null ise kendi listelerin. */
    suspend fun profilListeleri(kullaniciAdi: String?, imlec: String? = null): ListeSayfasi

    suspend fun liste(listeId: String): Liste

    suspend fun listeOlustur(istek: ListeIstegi): Liste

    suspend fun listeGuncelle(listeId: String, istek: ListeIstegi): Liste

    suspend fun listeSil(listeId: String)

    suspend fun listeBegen(listeId: String)

    suspend fun listeBegenisiniGeriAl(listeId: String)

    suspend fun oyunListeleri(oyunId: String): ListeSayfasi

    suspend fun kullaniciAra(sorgu: String, imlec: String? = null): KullaniciListesi

    suspend fun vitrinKaydet(oyunIdleri: List<String>): ProfilSayfasi
}

enum class KisiListesiTuru { TAKIPCILER, TAKIP_EDILENLER, ARKADASLAR }

/** Ayarlardaki listeler: engellediklerin ve sessize aldıkların. */
enum class EngelListesiTuru { ENGELLENENLER, SESSIZE_ALINANLAR }

/** Backend'in `ApiHatasi` gövdesiyle reddettiği istek. Bağlantı hataları bu değil, başka istisnalardır. */
class ApiHatasiIstisnasi(val kod: HataKodu, val ayrinti: String? = null) : Exception(kod.name)
