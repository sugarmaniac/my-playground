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
