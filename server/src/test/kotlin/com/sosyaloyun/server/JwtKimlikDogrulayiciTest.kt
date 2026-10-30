package com.sosyaloyun.server

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sosyaloyun.server.test.SahteSaat
import com.sosyaloyun.shared.KimlikSaglayici
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaInstant
import kotlinx.coroutines.test.runTest

/** Gerçek Apple/Google adaptörü: imza, yayıncı, kitle ve süre kontrolleri, yerel bir anahtar setiyle. */
class JwtKimlikDogrulayiciTest {
    private val saat = SahteSaat()
    private val appleAnahtari = RSAKeyGenerator(2048).keyID("apple-1").generate()
    private val googleAnahtari = RSAKeyGenerator(2048).keyID("google-1").generate()

    private val dogrulayici = JwtKimlikDogrulayici(
        saglayicilar = mapOf(
            KimlikSaglayici.APPLE to SaglayiciAyari(
                anahtarlar = ImmutableJWKSet(JWKSet(appleAnahtari.toPublicJWK())),
                yayincilar = setOf("https://appleid.apple.com"),
                kitleler = setOf("com.sosyaloyun.app"),
            ),
            KimlikSaglayici.GOOGLE to SaglayiciAyari(
                anahtarlar = ImmutableJWKSet(JWKSet(googleAnahtari.toPublicJWK())),
                yayincilar = setOf("https://accounts.google.com", "accounts.google.com"),
                kitleler = setOf("istemci.apps.googleusercontent.com"),
            ),
        ),
        saat = saat,
    )

    private fun token(
        anahtar: RSAKey = appleAnahtari,
        yayinci: String = "https://appleid.apple.com",
        kitle: String = "com.sosyaloyun.app",
        bitis: Date = Date.from((saat.now() + 1.hours).toJavaInstant()),
    ): String = SignedJWT(
        JWSHeader.Builder(JWSAlgorithm.RS256).keyID(anahtar.keyID).build(),
        JWTClaimsSet.Builder().subject("kullanici-sub").issuer(yayinci).audience(kitle).expirationTime(bitis).build(),
    ).apply { sign(RSASSASigner(anahtar)) }.serialize()

    @Test
    fun gecerliAppleTokeniKabulEdilir() = runTest {
        assertEquals(DogrulanmisKimlik("kullanici-sub"), dogrulayici.dogrula(KimlikSaglayici.APPLE, token()))
    }

    @Test
    fun gecerliGoogleTokeniKabulEdilir() = runTest {
        val google = token(anahtar = googleAnahtari, yayinci = "accounts.google.com", kitle = "istemci.apps.googleusercontent.com")
        assertEquals(DogrulanmisKimlik("kullanici-sub"), dogrulayici.dogrula(KimlikSaglayici.GOOGLE, google))
    }

    @Test
    fun baskaUygulamayaVerilmisTokenReddedilir() = runTest {
        assertNull(dogrulayici.dogrula(KimlikSaglayici.APPLE, token(kitle = "baska.uygulama")))
    }

    @Test
    fun suresiDolmusTokenReddedilir() = runTest {
        val tok = token()
        saat.ilerlet(2.hours)
        assertNull(dogrulayici.dogrula(KimlikSaglayici.APPLE, tok))
    }

    @Test
    fun baskaAnahtarlaImzalanmisTokenReddedilir() = runTest {
        val sahte = RSAKeyGenerator(2048).keyID("apple-1").generate()
        assertNull(dogrulayici.dogrula(KimlikSaglayici.APPLE, token(anahtar = sahte)))
    }

    @Test
    fun appleTokeniGoogleIcinKullanilamaz() = runTest {
        assertNull(dogrulayici.dogrula(KimlikSaglayici.GOOGLE, token()))
    }

    @Test
    fun bozukTokenReddedilir() = runTest {
        assertNull(dogrulayici.dogrula(KimlikSaglayici.APPLE, "bu-bir-jwt-degil"))
    }
}
