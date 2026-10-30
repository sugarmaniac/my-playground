package com.sosyaloyun.shared

object ApiYollari {
    const val SAGLIK = "/saglik"
    const val OTURUM = "/oturum"
    const val BEN = "/ben"
    const val DAVET_KULLAN = "/davet/kullan"
    const val DAVET_KODLARIM = "/davet/kodlarim"
    const val KULLANICI_ADI_MUSAITLIK = "/kullanici-adi/musaitlik"
    const val PROFIL = "/profil"

    /** Kendi profil sayfası ve sekmeleri. */
    const val PROFILIM = "/profilim"
    const val PROFILIM_GUNLUK = "/profilim/gunluk"
    const val PROFILIM_OYUNLAR = "/profilim/oyunlar"
    const val PROFILIM_INCELEMELER = "/profilim/incelemeler"
    const val PROFILIM_ISTEK_LISTESI = "/profilim/istek-listesi"
    const val PROFILIM_VITRIN = "/profilim/vitrin"

    /** Başkasının profili: `/kullanicilar/{kullaniciAdi}` ve aynı sekmeler; takip için `/takip`. */
    const val KULLANICILAR = "/kullanicilar"

    fun kullanici(kullaniciAdi: String) = "$KULLANICILAR/$kullaniciAdi"

    fun kullaniciSekmesi(kullaniciAdi: String, sekme: String) = "$KULLANICILAR/$kullaniciAdi/$sekme"

    const val SEKME_GUNLUK = "gunluk"
    const val SEKME_OYUNLAR = "oyunlar"
    const val SEKME_INCELEMELER = "incelemeler"
    const val SEKME_ISTEK_LISTESI = "istek-listesi"
    const val SEKME_TAKIPCILER = "takipciler"
    const val SEKME_TAKIP_EDILENLER = "takip-edilenler"
    const val TAKIP = "takip"
    const val ARKADASLIK = "arkadaslik"
    const val SEKME_ARKADASLAR = "arkadaslar"

    /** `PUT` engeller, `DELETE` engeli kaldırır (engellenen başka hiçbir uç noktada görünmez). */
    const val ENGEL = "engel"

    /** `PUT` sessize alır, `DELETE` geri alır. */
    const val SESSIZ = "sessiz"

    /** Ayarlar: engellediklerin ve sessize aldıkların. */
    const val ENGELLENENLER = "/engellenenler"
    const val SESSIZE_ALINANLAR = "/sessize-alinanlar"

    /** Arkadaşın profilinde ortak oyunlar ve istek listesi eşleşmeleri; yalnızca arkadaşlara açık. */
    const val SEKME_ORTAK = "ortak"

    /** Gelen arkadaşlık istekleri; `/{ad}/kabul` kabul eder, `DELETE /{ad}` reddeder. */
    const val ARKADASLIK_ISTEKLERI = "/arkadaslik-istekleri"

    fun arkadaslikIstegi(kullaniciAdi: String) = "$ARKADASLIK_ISTEKLERI/$kullaniciAdi"

    fun arkadaslikIstegiKabul(kullaniciAdi: String) = "$ARKADASLIK_ISTEKLERI/$kullaniciAdi/kabul"

    /** Akış: `?sekme=takip|arkadaslar&imlec=`; `/yeni?sonra=` daha yeni olay sayısı; `/oneriler` boş akış için. */
    const val AKIS = "/akis"
    const val AKIS_YENI = "/akis/yeni"
    const val AKIS_ONERILER = "/akis/oneriler"

    /** `POST /oyunlar/{id}/istek-listesi`: akış kartından tek dokunuşla istek listesine ekleme. */
    fun istekListesi(oyunId: String) = "$OYUNLAR/$oyunId/istek-listesi"

    /** Kullanıcı araması (`/kullanicilar/ara` değil: `ara` geçerli bir kullanıcı adı). */
    const val KULLANICI_ARA = "/kullanici-ara"
    const val OYUN_ARA = "/oyunlar/ara"
    const val POPULER_OYUNLAR = "/oyunlar/populer"

    const val KESIF = "/kesif"

    /** Web sitesinin oturumsuz, salt okunur paylaşım uç noktaları; yalnızca Herkes görünürlüğündeki içerik. */
    const val PAYLASIM = "/paylasim"
    fun paylasilanKayit(kayitId: String) = "$PAYLASIM/gunluk/$kayitId"
    fun paylasilanProfil(kullaniciAdi: String) = "$PAYLASIM/profil/$kullaniciAdi"

    const val SIKAYETLER = "/sikayetler"

    /** Moderatörün şifreyle (HTTP Basic) açtığı tek sayfalık şikayet listesi; uygulama kullanmaz. */
    const val MODERASYON = "/moderasyon"

    /** `GET` liste (son 200 bildirim, beğeniler gruplanmış). */
    const val BILDIRIMLER = "/bildirimler"

    /** `POST`: bütün bildirimleri okundu say. */
    const val BILDIRIMLER_OKUNDU = "/bildirimler/okundu"
    const val OKUNMAMIS_BILDIRIM_SAYISI = "/bildirimler/okunmamis-sayisi"

    /** `GET`/`PUT` tür başına push tercihleri. */
    const val BILDIRIM_TERCIHLERI = "/bildirim-tercihleri"

    /** `PUT` push token'ı kaydeder. */
    const val CIHAZ = "/cihaz"

    /** `GET ?tur=<Tur>` ya da `?platform=<IGDB platform adı>`, `&imlec=`: o türdeki ya da platformdaki oyunlar, popüler önce. */
    const val OYUN_LISTESI = "/oyunlar/liste"

    /** `GET` bağlantı, `PUT` bağla ve içe aktar, `DELETE` bağlantıyı kaldır. */
    const val STEAM = "/steam"

    /** `PUT` kendi telefon hash'ini kaydeder, `DELETE` siler. */
    const val TELEFONUM = "/telefonum"
    const val ARKADAS_BUL_REHBER = "/arkadas-bul/rehber"
    const val ARKADAS_BUL_STEAM = "/arkadas-bul/steam"
    const val TOPLU_TAKIP = "/takip/toplu"

    /** `POST`: onboarding'in son adımı bitti. */
    const val ONBOARDING_TAMAMLA = "/onboarding/tamamla"

    /** `GET /oyunlar/{id}`: oyun detayı; `PUT /oyunlar/{id}/kutuphane`: kütüphane girişi. */
    const val OYUNLAR = "/oyunlar"

    fun kutuphaneGirisi(oyunId: String) = "$OYUNLAR/$oyunId/kutuphane"

    /** `POST /oyunlar/{id}/gunluk`: yeni günlük kaydı. */
    fun oyunGunlugu(oyunId: String) = "$OYUNLAR/$oyunId/gunluk"

    /** `GET`: oyunun incelemeleri, önce arkadaşlarınki; `?imlec=` ile sayfalı. */
    fun oyunIncelemeleri(oyunId: String) = "$OYUNLAR/$oyunId/incelemeler"

    /** `GET` / `PUT` / `DELETE /gunluk/{id}`: günlük kaydı detayı, düzenleme ve silme. */
    const val GUNLUK = "/gunluk"

    /** `PUT` / `DELETE`: beğenme ve geri alma (CONTEXT.md: Beğeni). */
    fun begeni(kayitId: String) = "$GUNLUK/$kayitId/begeni"

    fun begenenler(kayitId: String) = "$GUNLUK/$kayitId/begenenler"

    /** IGDB webhook'ları: `/igdb/webhook/{create|update|delete}`. */
    const val IGDB_WEBHOOK = "/igdb/webhook"
}
