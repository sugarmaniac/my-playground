package com.sosyaloyun.app.oturum

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.sosyaloyun.shared.KimlikSaglayici
import kotlin.coroutines.resume
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

/** iOS'ta Apple ile giriş (AuthenticationServices). Google ile giriş iOS'ta henüz yok. */
private class IosKimlikSaglayiciIstemcisi : KimlikSaglayiciIstemcisi {
    override val desteklenenler: List<KimlikSaglayici> = listOf(KimlikSaglayici.APPLE)

    // Apple akışı sürerken temsilci canlı tutulmalı; denetleyici ona zayıf referans tutar.
    private var temsilci: AppleGirisTemsilcisi? = null

    override suspend fun kimlikTokeniAl(saglayici: KimlikSaglayici): KimlikTokeniSonucu {
        if (saglayici != KimlikSaglayici.APPLE) return KimlikTokeniSonucu.Hata
        return suspendCancellableCoroutine { devam ->
            val istek = ASAuthorizationAppleIDProvider().createRequest()
            istek.requestedScopes = emptyList<Any?>()
            val denetleyici = ASAuthorizationController(authorizationRequests = listOf(istek))
            val yeniTemsilci = AppleGirisTemsilcisi { sonuc ->
                temsilci = null
                if (devam.isActive) devam.resume(sonuc)
            }
            temsilci = yeniTemsilci
            denetleyici.delegate = yeniTemsilci
            denetleyici.presentationContextProvider = yeniTemsilci
            denetleyici.performRequests()
        }
    }
}

private class AppleGirisTemsilcisi(
    private val bitti: (KimlikTokeniSonucu) -> Unit,
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    @OptIn(BetaInteropApi::class)
    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization: ASAuthorization) {
        val veri = (didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential)?.identityToken
        val token = veri?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }
        bitti(if (token != null) KimlikTokeniSonucu.Token(token) else KimlikTokeniSonucu.Hata)
    }

    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithError: NSError) {
        // 1001: ASAuthorizationErrorCanceled, kullanıcı Apple sayfasını kapattı.
        bitti(if (didCompleteWithError.code == 1001L) KimlikTokeniSonucu.Iptal else KimlikTokeniSonucu.Hata)
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun presentationAnchorForAuthorizationController(controller: ASAuthorizationController): ASPresentationAnchor =
        UIApplication.sharedApplication.keyWindow ?: UIWindow()
}

@Composable
actual fun kimlikSaglayiciIstemcisi(): KimlikSaglayiciIstemcisi = remember { IosKimlikSaglayiciIstemcisi() }
