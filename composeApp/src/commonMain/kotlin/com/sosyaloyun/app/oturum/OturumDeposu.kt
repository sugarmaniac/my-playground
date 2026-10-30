package com.sosyaloyun.app.oturum

/** Oturum token'ının cihazda saklandığı yer; uygulama kapatılıp açılınca oturum buradan geri gelir. */
interface OturumDeposu {
    fun oku(): String?

    fun yaz(token: String?)
}
