package com.sosyaloyun.app.oturum

import com.russhwolf.settings.Settings

/** [Settings] üzerinde oturum deposu: Android'de uygulamaya özel SharedPreferences, iOS'ta Keychain. */
class AyarliOturumDeposu(private val ayarlar: Settings = oturumAyarlari()) : OturumDeposu {
    override fun oku(): String? = ayarlar.getStringOrNull(ANAHTAR)

    override fun yaz(token: String?) {
        if (token == null) ayarlar.remove(ANAHTAR) else ayarlar.putString(ANAHTAR, token)
    }

    private companion object {
        const val ANAHTAR = "oturum_tokeni"
    }
}

expect fun oturumAyarlari(): Settings
