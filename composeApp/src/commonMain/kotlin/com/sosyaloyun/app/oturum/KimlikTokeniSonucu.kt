package com.sosyaloyun.app.oturum

/** Apple/Google'ın cihazdaki giriş akışının sonucu. */
sealed interface KimlikTokeniSonucu {
    data class Token(val kimlikTokeni: String) : KimlikTokeniSonucu
    data object Iptal : KimlikTokeniSonucu
    data object Hata : KimlikTokeniSonucu
}
