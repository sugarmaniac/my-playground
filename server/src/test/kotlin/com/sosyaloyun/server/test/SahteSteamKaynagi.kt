package com.sosyaloyun.server.test

import com.sosyaloyun.server.SteamKaynagi
import com.sosyaloyun.server.SteamOyunlari
import com.sosyaloyun.server.SteamOyunu

/** Testte doldurulan Steam: özel adlar ve hesapların oyunları; [gizliHesaplar]'daki profiller gizlidir. */
class SahteSteamKaynagi : SteamKaynagi {
    val ozelAdlar = mutableMapOf<String, String>()
    val hesaplar = mutableMapOf<String, List<SteamOyunu>>()
    val gizliHesaplar = mutableSetOf<String>()

    /** Arkadaş listeleri; listesi olmayan hesabın arkadaş listesi gizlidir. */
    val arkadasListeleri = mutableMapOf<String, List<String>>()

    override suspend fun ozelAdiCoz(ozelAd: String): String? = ozelAdlar[ozelAd]

    override suspend fun oyunlar(steamId: String): SteamOyunlari =
        if (steamId in gizliHesaplar) SteamOyunlari.Gizli else SteamOyunlari.Acik(hesaplar[steamId].orEmpty())

    override suspend fun arkadaslar(steamId: String): List<String>? = arkadasListeleri[steamId]
}
