package com.sosyaloyun.app.test

import com.sosyaloyun.app.api.ApiHatasiIstisnasi
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.api.EngelListesiTuru
import com.sosyaloyun.app.api.KisiListesiTuru
import com.sosyaloyun.shared.AkisOgesi
import com.sosyaloyun.shared.AkisSayfasi
import com.sosyaloyun.shared.AkisSekmesi
import com.sosyaloyun.shared.Avatar
import com.sosyaloyun.shared.BildirimListesi
import com.sosyaloyun.shared.BildirimTercihleri
import com.sosyaloyun.shared.BildirimTuru
import com.sosyaloyun.shared.BulunanKisi
import com.sosyaloyun.shared.BenYaniti
import com.sosyaloyun.shared.DavetKodu
import com.sosyaloyun.shared.GirisYaniti
import com.sosyaloyun.shared.KimlikSaglayici
import com.sosyaloyun.shared.KullaniciAdiDurumu
import com.sosyaloyun.shared.KullaniciAdiMusaitlikYaniti
import com.sosyaloyun.shared.KullaniciListesi
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.KutuphaneGirisi
import com.sosyaloyun.shared.KutuphaneOgesi
import com.sosyaloyun.shared.KutuphaneSayfasi
import com.sosyaloyun.shared.GunlukKaydi
import com.sosyaloyun.shared.GunlukKaydiDetayi
import com.sosyaloyun.shared.GunlukKaydiIstegi
import com.sosyaloyun.shared.GunlukOgesi
import com.sosyaloyun.shared.GunlukSayfasi
import com.sosyaloyun.shared.HataKodu
import com.sosyaloyun.shared.IncelemeListesi
import com.sosyaloyun.shared.Kesif
import com.sosyaloyun.shared.Tur
import com.sosyaloyun.shared.OrtakOyunlar
import com.sosyaloyun.shared.OyunAramaYaniti
import com.sosyaloyun.shared.OyunDetayi
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.ProfilIstegi
import com.sosyaloyun.shared.ProfilSayfasi
import com.sosyaloyun.shared.SaglikYaniti
import com.sosyaloyun.shared.SikayetIstegi
import com.sosyaloyun.shared.SteamBaglantisi
import com.sosyaloyun.shared.SteamIceAktarmaSonucu
import com.sosyaloyun.shared.SunucuDurumu
import kotlin.time.Instant

/** Yanıtları testte belirlenen API istemcisi; `hata` doluysa her istek onu fırlatır. */
class SahteApiIstemcisi : ApiIstemcisi {
    var saglikYaniti = SaglikYaniti(
        durum = SunucuDurumu.AYAKTA,
        apiSurumu = SaglikYaniti.API_SURUMU,
        sunucuZamani = Instant.parse("2026-01-05T09:00:00Z"),
    )
    var girisYaniti = GirisYaniti(oturumTokeni = "oturum-1", kullaniciId = "kullanici-1", yeniKullanici = true)
    var benYaniti = BenYaniti(kullaniciId = "kullanici-1", davetKabulEdildi = false, profil = null)
    var davetKodlari = emptyList<DavetKodu>()
    var alinmisAdlar = setOf<String>()
    var hata: Exception? = null

    /** Yalnızca bu çağrılar için hata; [hata]'dan önce bakılır. */
    var davetKoduHatasi: Exception? = null
    var profilHatasi: Exception? = null

    val girisler = mutableListOf<Pair<KimlikSaglayici, String>>()
    val kullanilanDavetKodlari = mutableListOf<String>()
    val musaitlikSorgulari = mutableListOf<String>()
    val kaydedilenProfiller = mutableListOf<ProfilIstegi>()
    var cikisSayisi = 0

    override suspend fun saglik(): SaglikYaniti {
        hata?.let { throw it }
        return saglikYaniti
    }

    override suspend fun girisYap(saglayici: KimlikSaglayici, kimlikTokeni: String): GirisYaniti {
        hata?.let { throw it }
        girisler += saglayici to kimlikTokeni
        return girisYaniti
    }

    override suspend fun cikisYap() {
        cikisSayisi++
        hata?.let { throw it }
    }

    override suspend fun ben(): BenYaniti {
        hata?.let { throw it }
        return benYaniti
    }

    override suspend fun davetKoduKullan(kod: String) {
        davetKoduHatasi?.let { throw it }
        hata?.let { throw it }
        kullanilanDavetKodlari += kod
        benYaniti = benYaniti.copy(davetKabulEdildi = true)
    }

    override suspend fun davetKodlarim(): List<DavetKodu> {
        hata?.let { throw it }
        return davetKodlari
    }

    override suspend fun kullaniciAdiMusaitlik(ad: String): KullaniciAdiMusaitlikYaniti {
        hata?.let { throw it }
        musaitlikSorgulari += ad
        return KullaniciAdiMusaitlikYaniti(ad, if (ad in alinmisAdlar) KullaniciAdiDurumu.ALINMIS else KullaniciAdiDurumu.MUSAIT)
    }

    /** Kataloğun sahtesi: adında sorgu geçen oyunlar, [aramaSayfasi]'nca sayfalanır. */
    var katalog = emptyList<OyunOzeti>()
    var aramaSayfasi = 20
    val aramalar = mutableListOf<Pair<String, String?>>()

    override suspend fun oyunAra(sorgu: String, imlec: String?): OyunAramaYaniti {
        hata?.let { throw it }
        aramalar += sorgu to imlec
        val eslesenler = katalog.filter { it.ad.contains(sorgu, ignoreCase = true) }
        val baslangic = imlec?.toInt() ?: 0
        val sonraki = baslangic + aramaSayfasi
        return OyunAramaYaniti(
            oyunlar = eslesenler.drop(baslangic).take(aramaSayfasi),
            sonrakiImlec = if (sonraki < eslesenler.size) sonraki.toString() else null,
        )
    }

    var oyunDetaylari = emptyMap<String, OyunDetayi>()

    override suspend fun oyunDetayi(oyunId: String): OyunDetayi {
        hata?.let { throw it }
        return oyunDetaylari[oyunId] ?: throw ApiHatasiIstisnasi(HataKodu.OYUN_BULUNAMADI)
    }

    var bildirimListesi = BildirimListesi(emptyList(), 0)
    var bildirimTercihleri = BildirimTercihleri(BildirimTuru.entries.associateWith { true })
    var bildirimHatasi: Exception? = null
    val bildirimIstekleri = mutableListOf<String>()

    override suspend fun bildirimler(): BildirimListesi {
        bildirimHatasi?.let { throw it }
        return bildirimListesi
    }

    override suspend fun bildirimleriOkunduSay() {
        bildirimHatasi?.let { throw it }
        bildirimIstekleri += "okundu"
    }

    override suspend fun okunmamisBildirimSayisi(): Int {
        bildirimHatasi?.let { throw it }
        return bildirimListesi.okunmamisSayisi
    }

    override suspend fun bildirimTercihleri(): BildirimTercihleri {
        bildirimHatasi?.let { throw it }
        return bildirimTercihleri
    }

    override suspend fun bildirimTercihleriniKaydet(tercihler: BildirimTercihleri): BildirimTercihleri {
        bildirimHatasi?.let { throw it }
        bildirimIstekleri += "tercih:${tercihler.push}"
        bildirimTercihleri = BildirimTercihleri(bildirimTercihleri.push + tercihler.push)
        return bildirimTercihleri
    }

    val sikayetler = mutableListOf<SikayetIstegi>()
    var sikayetHatasi: Exception? = null

    override suspend fun sikayetEt(istek: SikayetIstegi) {
        sikayetHatasi?.let { throw it }
        sikayetler += istek
    }

    var kesifYaniti: Kesif? = null
    /** Sorgu ("tur:ROL_YAPMA" ya da "platform:PC") ve imlece göre sayfalar. */
    var oyunListeleri = emptyMap<Pair<String, String?>, OyunAramaYaniti>()
    val oyunListesiSorgulari = mutableListOf<String>()

    override suspend fun kesif(): Kesif {
        hata?.let { throw it }
        return kesifYaniti!!
    }

    override suspend fun oyunListesi(tur: Tur?, platform: String?, imlec: String?): OyunAramaYaniti {
        hata?.let { throw it }
        val sorgu = tur?.let { "tur:${it.name}" } ?: "platform:$platform"
        oyunListesiSorgulari += "$sorgu:$imlec"
        return oyunListeleri[sorgu to imlec] ?: OyunAramaYaniti(emptyList(), null)
    }

    /** İmleçe göre inceleme sayfaları; ilk sayfanın imleci null. */
    var incelemeSayfalari = emptyMap<String?, IncelemeListesi>()
    var incelemeHatasi: Exception? = null
    val incelemeSorgulari = mutableListOf<String?>()

    override suspend fun oyunIncelemeleri(oyunId: String, imlec: String?): IncelemeListesi {
        incelemeHatasi?.let { throw it }
        hata?.let { throw it }
        incelemeSorgulari += imlec
        return incelemeSayfalari[imlec] ?: IncelemeListesi(emptyList())
    }

    val kutuphaneKayitlari = mutableListOf<Pair<String, KutuphaneGirisi>>()
    var kutuphaneHatasi: Exception? = null

    override suspend fun kutuphaneGirisiKaydet(oyunId: String, giris: KutuphaneGirisi): KutuphaneGirisi {
        kutuphaneHatasi?.let { throw it }
        hata?.let { throw it }
        kutuphaneKayitlari += oyunId to giris
        return giris
    }

    val girilenGunlukler = mutableListOf<Pair<String, GunlukKaydiIstegi>>()
    val duzenlenenGunlukler = mutableListOf<Pair<String, GunlukKaydiIstegi>>()
    val silinenGunlukler = mutableListOf<String>()
    var gunlukHatasi: Exception? = null

    private fun kayit(id: String, oyunId: String, istek: GunlukKaydiIstegi) = GunlukKaydi(
        id, oyunId, istek.platform, istek.baslama, istek.bitis, istek.sureDakika, null, istek.inceleme,
        istek.spoiler, istek.gorunurluk, istek.sessiz, Instant.parse("2026-01-05T09:00:00Z"),
    )

    override suspend fun gunlukKaydiGir(oyunId: String, istek: GunlukKaydiIstegi): GunlukKaydi {
        gunlukHatasi?.let { throw it }
        girilenGunlukler += oyunId to istek
        return kayit("yeni-kayit", oyunId, istek)
    }

    override suspend fun gunlukKaydiDuzenle(kayitId: String, istek: GunlukKaydiIstegi): GunlukKaydi {
        gunlukHatasi?.let { throw it }
        duzenlenenGunlukler += kayitId to istek
        return kayit(kayitId, "oyun", istek)
    }

    override suspend fun gunlukKaydiSil(kayitId: String) {
        gunlukHatasi?.let { throw it }
        silinenGunlukler += kayitId
    }

    var profilSayfasi = ProfilSayfasi(
        KullaniciOzeti("kullanici-1", "ayse", "Ayşe", Avatar.TILKI), biyografi = null, buYilBitirilen = 0, vitrin = emptyList(),
    )
    var gunlukOgeleri = emptyList<GunlukOgesi>()
    var kutuphaneOgeleri = emptyList<KutuphaneOgesi>()
    var profilSayfaBoyu = 20
    val profilSorgulari = mutableListOf<String>()
    val kaydedilenVitrinler = mutableListOf<List<String>>()

    val istenenProfiller = mutableListOf<String?>()

    override suspend fun profil(kullaniciAdi: String?): ProfilSayfasi {
        hata?.let { throw it }
        istenenProfiller += kullaniciAdi
        return profilSayfasi
    }

    val takipIstekleri = mutableListOf<String>()
    var takipHatasi: Exception? = null
    var kullanicilar = emptyList<KullaniciOzeti>()

    override suspend fun takipEt(kullaniciAdi: String) {
        takipHatasi?.let { throw it }
        takipIstekleri += "takip:$kullaniciAdi"
    }

    override suspend fun takibiBirak(kullaniciAdi: String) {
        takipHatasi?.let { throw it }
        takipIstekleri += "birak:$kullaniciAdi"
    }

    override suspend fun kisiListesi(kullaniciAdi: String, tur: KisiListesiTuru, imlec: String?): KullaniciListesi {
        hata?.let { throw it }
        takipIstekleri += "liste:$kullaniciAdi:$tur:$imlec"
        val (ogeler, sonraki) = sayfala(kullanicilar, imlec)
        return KullaniciListesi(ogeler, sonraki)
    }

    var ortak = OrtakOyunlar(emptyList(), emptyList())

    override suspend fun arkadaslikIste(kullaniciAdi: String) {
        takipHatasi?.let { throw it }
        takipIstekleri += "istek:$kullaniciAdi"
    }

    override suspend fun arkadasligiBitir(kullaniciAdi: String) {
        takipHatasi?.let { throw it }
        takipIstekleri += "bitir:$kullaniciAdi"
    }

    override suspend fun arkadaslikKabulEt(kullaniciAdi: String) {
        takipHatasi?.let { throw it }
        takipIstekleri += "kabul:$kullaniciAdi"
    }

    override suspend fun arkadaslikReddet(kullaniciAdi: String) {
        takipHatasi?.let { throw it }
        takipIstekleri += "ret:$kullaniciAdi"
    }

    override suspend fun gelenArkadaslikIstekleri(imlec: String?): KullaniciListesi {
        hata?.let { throw it }
        return KullaniciListesi(kullanicilar, null)
    }

    override suspend fun ortakOyunlar(kullaniciAdi: String): OrtakOyunlar {
        hata?.let { throw it }
        takipIstekleri += "ortak:$kullaniciAdi"
        return ortak
    }

    var akisOgeleri = emptyList<AkisOgesi>()
    var akisYeni = 0
    var akisOnerileriListesi = emptyList<KullaniciOzeti>()
    val akisIstekleri = mutableListOf<String>()
    var istekListesiHatasi: Exception? = null

    override suspend fun akis(sekme: AkisSekmesi, imlec: String?): AkisSayfasi {
        hata?.let { throw it }
        akisIstekleri += "akis:$sekme:$imlec"
        val (ogeler, sonraki) = sayfala(akisOgeleri, imlec)
        return AkisSayfasi(ogeler, sonraki, enYeniImlec = if (imlec == null) "en-yeni" else null)
    }

    override suspend fun akisYeniSayisi(sekme: AkisSekmesi, sonra: String?): Int {
        hata?.let { throw it }
        akisIstekleri += "yeni:$sekme:$sonra"
        return akisYeni
    }

    override suspend fun akisOnerileri(): List<KullaniciOzeti> {
        hata?.let { throw it }
        return akisOnerileriListesi
    }

    override suspend fun istekListesineEkle(oyunId: String) {
        istekListesiHatasi?.let { throw it }
        akisIstekleri += "istek-listesi:$oyunId"
    }

    var gunlukDetaylari = emptyMap<String, GunlukKaydiDetayi>()
    var begenenListesi = emptyList<KullaniciOzeti>()
    val begeniIstekleri = mutableListOf<String>()
    var begeniHatasi: Exception? = null

    override suspend fun gunlukKaydiDetayi(kayitId: String): GunlukKaydiDetayi {
        hata?.let { throw it }
        return gunlukDetaylari[kayitId] ?: throw ApiHatasiIstisnasi(HataKodu.GUNLUK_KAYDI_BULUNAMADI)
    }

    var steamBaglantisi: SteamBaglantisi? = null
    var steamSonucu: SteamIceAktarmaSonucu? = null
    var steamHatasi: Exception? = null
    var populerler = emptyList<OyunOzeti>()
    var onboardingHatasi: Exception? = null
    val onboardingIstekleri = mutableListOf<String>()

    override suspend fun steamBaglantisi(): SteamBaglantisi? {
        hata?.let { throw it }
        return steamBaglantisi
    }

    override suspend fun steamBagla(girdi: String): SteamIceAktarmaSonucu {
        steamHatasi?.let { throw it }
        onboardingIstekleri += "steam:$girdi"
        return steamSonucu!!.also { steamBaglantisi = it.baglanti }
    }

    override suspend fun steamBaglantisiniKaldir() {
        steamHatasi?.let { throw it }
        onboardingIstekleri += "steamKaldir"
        steamBaglantisi = null
    }

    override suspend fun populerOyunlar(): List<OyunOzeti> {
        hata?.let { throw it }
        return populerler
    }

    override suspend fun onboardingiTamamla() {
        onboardingHatasi?.let { throw it }
        onboardingIstekleri += "tamamla"
    }

    var rehberEslesmeleri = emptyList<BulunanKisi>()
    var steamArkadaslari = emptyList<BulunanKisi>()
    var arkadasBulmaHatasi: Exception? = null
    val gonderilenHashler = mutableListOf<String>()
    val topluTakipler = mutableListOf<List<String>>()

    override suspend fun telefonumuKaydet(hash: String) {
        arkadasBulmaHatasi?.let { throw it }
        onboardingIstekleri += "telefon:$hash"
    }

    override suspend fun rehberdenBul(hashler: List<String>): List<BulunanKisi> {
        arkadasBulmaHatasi?.let { throw it }
        gonderilenHashler += hashler
        return rehberEslesmeleri
    }

    override suspend fun steamdenBul(): List<BulunanKisi> {
        arkadasBulmaHatasi?.let { throw it }
        return steamArkadaslari
    }

    override suspend fun topluTakip(kullaniciAdlari: List<String>) {
        arkadasBulmaHatasi?.let { throw it }
        topluTakipler += kullaniciAdlari
    }

    /** Engel ve sessiz işlemleri sırayla: "engelle:ad", "sessiz:ad" gibi. */
    val engelIstekleri = mutableListOf<String>()
    var engelHatasi: Exception? = null
    var engelListeleri = mapOf<EngelListesiTuru, List<KullaniciOzeti>>()

    private fun engelIstegi(istek: String) {
        engelHatasi?.let { throw it }
        engelIstekleri += istek
    }

    override suspend fun engelle(kullaniciAdi: String) = engelIstegi("engelle:$kullaniciAdi")

    override suspend fun engeliKaldir(kullaniciAdi: String) = engelIstegi("engeliKaldir:$kullaniciAdi")

    override suspend fun sessizeAl(kullaniciAdi: String) = engelIstegi("sessiz:$kullaniciAdi")

    override suspend fun sessizdenCikar(kullaniciAdi: String) = engelIstegi("sessizdenCikar:$kullaniciAdi")

    override suspend fun engelListesi(tur: EngelListesiTuru, imlec: String?): KullaniciListesi {
        hata?.let { throw it }
        return KullaniciListesi(engelListeleri[tur].orEmpty(), null)
    }

    override suspend fun begen(kayitId: String) {
        begeniHatasi?.let { throw it }
        begeniIstekleri += "begen:$kayitId"
    }

    override suspend fun begeniyiGeriAl(kayitId: String) {
        begeniHatasi?.let { throw it }
        begeniIstekleri += "geri:$kayitId"
    }

    override suspend fun begenenler(kayitId: String): List<KullaniciOzeti> {
        hata?.let { throw it }
        begeniIstekleri += "begenenler:$kayitId"
        return begenenListesi
    }

    val kullaniciAramalari = mutableListOf<String>()

    override suspend fun kullaniciAra(sorgu: String, imlec: String?): KullaniciListesi {
        hata?.let { throw it }
        kullaniciAramalari += sorgu
        return KullaniciListesi(kullanicilar.filter { it.kullaniciAdi.contains(sorgu, ignoreCase = true) }, null)
    }

    private fun <T> sayfala(liste: List<T>, imlec: String?): Pair<List<T>, String?> {
        val baslangic = imlec?.toInt() ?: 0
        val sonraki = baslangic + profilSayfaBoyu
        return liste.drop(baslangic).take(profilSayfaBoyu) to (if (sonraki < liste.size) sonraki.toString() else null)
    }

    override suspend fun profilGunlugu(kullaniciAdi: String?, yalnizcaIncelemeler: Boolean, imlec: String?): GunlukSayfasi {
        hata?.let { throw it }
        profilSorgulari += "gunluk:$yalnizcaIncelemeler:$imlec"
        val (ogeler, sonraki) = sayfala(gunlukOgeleri.filter { !yalnizcaIncelemeler || it.kayit.inceleme != null }, imlec)
        return GunlukSayfasi(ogeler, sonraki)
    }

    override suspend fun profilKutuphanesi(kullaniciAdi: String?, istekListesi: Boolean, durum: String?, imlec: String?): KutuphaneSayfasi {
        hata?.let { throw it }
        profilSorgulari += "kutuphane:$istekListesi:$durum:$imlec"
        val suzulmus = kutuphaneOgeleri.filter {
            if (istekListesi) it.giris.istekListesinde else durum == null || it.giris.durum?.name.equals(durum, ignoreCase = true)
        }
        val (ogeler, sonraki) = sayfala(suzulmus, imlec)
        return KutuphaneSayfasi(ogeler, sonraki)
    }

    override suspend fun vitrinKaydet(oyunIdleri: List<String>): ProfilSayfasi {
        hata?.let { throw it }
        kaydedilenVitrinler += oyunIdleri
        return profilSayfasi
    }

    override suspend fun profilKaydet(istek: ProfilIstegi): BenYaniti {
        profilHatasi?.let { throw it }
        hata?.let { throw it }
        kaydedilenProfiller += istek
        benYaniti = benYaniti.copy(
            profil = KullaniciOzeti(benYaniti.kullaniciId, istek.kullaniciAdi, istek.gorunenAd, istek.avatar),
        )
        return benYaniti
    }
}
