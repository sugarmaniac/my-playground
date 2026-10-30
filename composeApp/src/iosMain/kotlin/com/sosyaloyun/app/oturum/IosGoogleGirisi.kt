package com.sosyaloyun.app.oturum

/**
 * iOS'ta Google ile giriş (#32). GoogleSignIn yalnızca Swift Package Manager ile Xcode projesine eklenebildiği için
 * akışın kendisi Swift'tedir (`iosApp/iosApp/GoogleSignInGirisi.swift`) ve uygulama açılırken [MainViewController]'a verilir.
 */
interface IosGoogleGirisi {
    /** Info.plist'te iOS OAuth istemci kimliği (`GIDClientID`) tanımlıysa true; değilse düğme gösterilmez. */
    val kullanilabilir: Boolean

    /** [sunucuIstemciKimligi] boşsa verilmez. Sonuç tam olarak bir kez [sonuc]'a bildirilir. */
    fun girisYap(sunucuIstemciKimligi: String, sonuc: IosGoogleGirisSonucu)
}

/** Swift tarafının sonucu bildirdiği nesne; Kotlin lambda'ları yerine sade yöntemler Swift'ten çağrılması kolay. */
class IosGoogleGirisSonucu internal constructor(private val bitti: (KimlikTokeniSonucu) -> Unit) {
    fun token(deger: String) = bitti(KimlikTokeniSonucu.Token(deger))

    fun iptal() = bitti(KimlikTokeniSonucu.Iptal)

    fun hata() = bitti(KimlikTokeniSonucu.Hata)
}

/** Uygulama açılırken [MainViewController] tarafından verilir. */
internal var iosGoogleGirisi: IosGoogleGirisi? = null
