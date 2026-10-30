package com.sosyaloyun.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Hata yanıtlarının gövdesi; istemci mesajı `kod`'a göre kendi dilinde seçer. */
@Serializable
data class ApiHatasi(val kod: HataKodu)

@Serializable
enum class HataKodu {
    @SerialName("kimlik_tokeni_gecersiz")
    KIMLIK_TOKENI_GECERSIZ,

    @SerialName("oturum_gerekli")
    OTURUM_GEREKLI,

    @SerialName("davet_kodu_gecersiz")
    DAVET_KODU_GECERSIZ,

    @SerialName("davet_kodu_kullanilmis")
    DAVET_KODU_KULLANILMIS,

    /** Kullanıcı henüz bir davet koduyla betaya katılmadı. */
    @SerialName("davet_gerekli")
    DAVET_GEREKLI,

    @SerialName("kullanici_adi_gecersiz")
    KULLANICI_ADI_GECERSIZ,

    @SerialName("kullanici_adi_alinmis")
    KULLANICI_ADI_ALINMIS,

    @SerialName("gorunen_ad_gecersiz")
    GORUNEN_AD_GECERSIZ,

    @SerialName("oyun_bulunamadi")
    OYUN_BULUNAMADI,

    @SerialName("puan_gecersiz")
    PUAN_GECERSIZ,

    /** %100 rozeti yalnızca Bitirdim durumundaki bir kütüphane girişine eklenebilir. */
    @SerialName("yuzde_yuz_yalnizca_bitirdim")
    YUZDE_YUZ_YALNIZCA_BITIRDIM,

    @SerialName("gunluk_kaydi_bulunamadi")
    GUNLUK_KAYDI_BULUNAMADI,

    /** Bitirme tarihi başlama tarihinden önce. */
    @SerialName("tarih_gecersiz")
    TARIH_GECERSIZ,

    @SerialName("sure_gecersiz")
    SURE_GECERSIZ,

    @SerialName("inceleme_cok_uzun")
    INCELEME_COK_UZUN,

    @SerialName("platform_gecersiz")
    PLATFORM_GECERSIZ,

    @SerialName("biyografi_cok_uzun")
    BIYOGRAFI_COK_UZUN,

    /** Vitrin en fazla 4 farklı, katalogda olan oyundan oluşur. */
    @SerialName("vitrin_gecersiz")
    VITRIN_GECERSIZ,

    @SerialName("kullanici_bulunamadi")
    KULLANICI_BULUNAMADI,

    @SerialName("kendini_takip_edemezsin")
    KENDINI_TAKIP_EDEMEZSIN,

    @SerialName("kendine_istek_gonderemezsin")
    KENDINE_ISTEK_GONDEREMEZSIN,

    /** Arkadaşlık takibi kapsar; arkadaşlık sürerken takipten çıkılamaz (CONTEXT.md: Arkadaşlık). */
    @SerialName("arkadaskan_takipten_cikilamaz")
    ARKADASKEN_TAKIPTEN_CIKILAMAZ,

    @SerialName("arkadaslik_istegi_yok")
    ARKADASLIK_ISTEGI_YOK,

    @SerialName("arkadas_degil")
    ARKADAS_DEGIL,
}
