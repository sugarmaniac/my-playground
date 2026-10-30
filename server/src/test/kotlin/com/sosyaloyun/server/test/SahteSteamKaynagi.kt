package com.sosyaloyun.server.test

import com.sosyaloyun.server.SteamKaynagi
import com.sosyaloyun.server.SteamOyunlari
import com.sosyaloyun.server.SteamOyunu

/** Testte doldurulan Steam: özel adlar ve hesapların oyunları; [gizliHesaplar]'daki profiller gizlidir. */
class SahteSteamKaynagi : SteamKaynagi {
    val ozelAdlar = mutableMapOf<String, String>()
    val hesaplar = mutableMapOf<String, List<SteamOyunu>>()
    val gizliHesaplar = mutableSetOf<String>()

    override suspend fun ozelAdiCoz(ozelAd: String): String? = ozelAdlar[ozelAd]

    override suspend fun oyunlar(steamId: String): SteamOyunlari =
        if (steamId in gizliHesaplar) SteamOyunlari.Gizli else SteamOyunlari.Acik(hesaplar[steamId].orEmpty())
}
