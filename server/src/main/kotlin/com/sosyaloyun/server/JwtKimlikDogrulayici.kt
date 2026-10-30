package com.sosyaloyun.server

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import com.sosyaloyun.shared.KimlikSaglayici
import java.net.URI
import java.util.Date
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

/** Bir sağlayıcının imza anahtarları, kabul edilen yayıncılar (`iss`) ve bu uygulamanın kimlikleri (`aud`). */
class SaglayiciAyari(
    val anahtarlar: JWKSource<SecurityContext>,
    val yayincilar: Set<String>,
    val kitleler: Set<String>,
)

/** Apple ve Google kimlik token'larını (RS256 imzalı JWT) sağlayıcıların yayımladığı anahtarlarla doğrular. */
class JwtKimlikDogrulayici(
    private val saglayicilar: Map<KimlikSaglayici, SaglayiciAyari>,
    private val saat: Clock,
) : KimlikDogrulayici {
    override suspend fun dogrula(saglayici: KimlikSaglayici, kimlikTokeni: String): DogrulanmisKimlik? {
        val ayar = saglayicilar[saglayici]?.takeIf { it.kitleler.isNotEmpty() } ?: return null
        val islemci = DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, ayar.anahtarlar)
            jwtClaimsSetVerifier = object : DefaultJWTClaimsVerifier<SecurityContext>(
                ayar.kitleler,
                null,
                setOf("sub", "iss", "aud", "exp"),
                null,
            ) {
                override fun currentTime(): Date = Date.from(saat.now().toJavaInstant())
            }
        }
        val iddialar: JWTClaimsSet = try {
            withContext(Dispatchers.IO) { islemci.process(kimlikTokeni, null) }
        } catch (e: Exception) {
            return null
        }
        if (iddialar.issuer !in ayar.yayincilar) return null
        return DogrulanmisKimlik(haricKimlik = iddialar.subject)
    }

    companion object {
        private val log = LoggerFactory.getLogger(JwtKimlikDogrulayici::class.java)

        /**
         * Kitleler ortam değişkenlerinden okunur (virgülle ayrılmış):
         * `APPLE_KIMLIK_KITLELERI` (iOS bundle id, varsayılan `com.sosyaloyun.app`),
         * `GOOGLE_KIMLIK_KITLELERI` (Google OAuth istemci kimlikleri). Kitlesi boş sağlayıcıyla giriş reddedilir.
         */
        fun ortamdan(saat: Clock): JwtKimlikDogrulayici {
            fun kitleler(ad: String, varsayilan: String = "") =
                (System.getenv(ad) ?: varsayilan).split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

            val apple = kitleler("APPLE_KIMLIK_KITLELERI", varsayilan = "com.sosyaloyun.app")
            val google = kitleler("GOOGLE_KIMLIK_KITLELERI")
            if (google.isEmpty()) log.warn("GOOGLE_KIMLIK_KITLELERI tanımlı değil; Google ile giriş reddedilecek.")

            return JwtKimlikDogrulayici(
                saglayicilar = mapOf(
                    KimlikSaglayici.APPLE to SaglayiciAyari(
                        anahtarlar = uzakAnahtarlar("https://appleid.apple.com/auth/keys"),
                        yayincilar = setOf("https://appleid.apple.com"),
                        kitleler = apple,
                    ),
                    KimlikSaglayici.GOOGLE to SaglayiciAyari(
                        anahtarlar = uzakAnahtarlar("https://www.googleapis.com/oauth2/v3/certs"),
                        yayincilar = setOf("https://accounts.google.com", "accounts.google.com"),
                        kitleler = google,
                    ),
                ),
                saat = saat,
            )
        }

        private fun uzakAnahtarlar(adres: String): JWKSource<SecurityContext> =
            JWKSourceBuilder.create<SecurityContext>(URI(adres).toURL()).retrying(true).build()
    }
}
