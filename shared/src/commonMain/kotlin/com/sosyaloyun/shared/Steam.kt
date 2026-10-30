package com.sosyaloyun.shared

import kotlinx.serialization.Serializable

/** `PUT /steam`: profil linki, SteamID64 ya da özel profil adı. */
@Serializable
data class SteamBaglaIstegi(val girdi: String)

/** Bağlama ve içe aktarmanın sonucu: en az bir kez açılmış oyunlar durumsuz kütüphane girişi olur. */
@Serializable
data class SteamIceAktarmaSonucu(
    val baglanti: SteamBaglantisi,
    val iceAktarilanSayisi: Int,
    /** Steam süresine göre en çok oynanan 10 oyun; onboarding'de durumları seçtirilir. */
    val enCokOynananlar: List<KutuphaneOgesi>,
)

/** `GET /steam`: bağlı hesap; bağlı değilse 404 `STEAM_BAGLI_DEGIL`. */
@Serializable
data class SteamBaglantisi(val steamId: String)

/** `GET /oyunlar/populer`: Steam bağlamayan kullanıcının onboarding'de işaretleyeceği oyunlar. */
@Serializable
data class PopulerOyunlar(val oyunlar: List<OyunOzeti>)

object OnboardingKurallari {
    /** Steam bağlamayan kullanıcının onboarding'i bitirmek için durum seçmesi gereken en az oyun sayısı. */
    const val EN_AZ_OYUN = 3
}
