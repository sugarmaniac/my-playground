package com.sosyaloyun.server

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Date
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.slf4j.LoggerFactory

/** Push bildirimi gönderen adaptör (FCM; iOS cihazlara Firebase üzerinden APNs ile gider). */
interface PushGonderici {
    suspend fun gonder(token: String, baslik: String, metin: String)
}

/**
 * Firebase Cloud Messaging HTTP v1. Hizmet hesabı JSON'u `FCM_HIZMET_HESABI` ortam değişkeninden okunur; erişim token'ı
 * hizmet hesabının anahtarıyla imzalanmış JWT karşılığında alınır ve süresi dolana kadar saklanır.
 */
class FcmPushGonderici(hizmetHesabiJson: String, private val http: HttpClient = HttpClient(CIO)) : PushGonderici {
    private val json = Json { ignoreUnknownKeys = true }
    private val hesap = json.decodeFromString(HizmetHesabi.serializer(), hizmetHesabiJson)
    private val anahtar = KeyFactory.getInstance("RSA").generatePrivate(
        PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(hesap.privateKey.replace(Regex("-----[A-Z ]+-----"), ""))),
    ) as RSAPrivateKey
    private val kilit = Mutex()
    private var erisim: String? = null
    private var erisimBitisi = 0L

    override suspend fun gonder(token: String, baslik: String, metin: String) {
        val govde = buildJsonObject {
            putJsonObject("message") {
                put("token", token)
                putJsonObject("notification") {
                    put("title", baslik)
                    put("body", metin)
                }
            }
        }
        val yanit = http.post("https://fcm.googleapis.com/v1/projects/${hesap.projectId}/messages:send") {
            bearerAuth(erisimTokeni())
            contentType(ContentType.Application.Json)
            setBody(govde.toString())
        }
        check(yanit.status.isSuccess()) { "FCM gönderimi başarısız: ${yanit.status} ${yanit.bodyAsText().take(300)}" }
    }

    private suspend fun erisimTokeni(): String = kilit.withLock {
        val simdi = System.currentTimeMillis()
        erisim?.takeIf { simdi < erisimBitisi - 60_000 }?.let { return it }
        val iddia = JWTClaimsSet.Builder()
            .issuer(hesap.clientEmail)
            .audience(TOKEN_ADRESI)
            .claim("scope", "https://www.googleapis.com/auth/firebase.messaging")
            .issueTime(Date(simdi))
            .expirationTime(Date(simdi + 3_600_000))
            .build()
        val jwt = SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(), iddia).apply { sign(RSASSASigner(anahtar)) }
        val yanit = http.submitForm(
            TOKEN_ADRESI,
            parameters {
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("assertion", jwt.serialize())
            },
        )
        check(yanit.status.isSuccess()) { "FCM erişim token'ı alınamadı: ${yanit.status}" }
        val token = json.decodeFromString(TokenYaniti.serializer(), yanit.bodyAsText())
        erisim = token.accessToken
        erisimBitisi = simdi + token.expiresIn * 1000
        token.accessToken
    }

    @Serializable
    private data class HizmetHesabi(
        @SerialName("project_id") val projectId: String,
        @SerialName("client_email") val clientEmail: String,
        @SerialName("private_key") val privateKey: String,
    )

    @Serializable
    private data class TokenYaniti(@SerialName("access_token") val accessToken: String, @SerialName("expires_in") val expiresIn: Long)

    companion object {
        private const val TOKEN_ADRESI = "https://oauth2.googleapis.com/token"
        private val log = LoggerFactory.getLogger(FcmPushGonderici::class.java)

        fun ortamdan(): FcmPushGonderici? {
            val hesap = System.getenv("FCM_HIZMET_HESABI").orEmpty()
            if (hesap.isBlank()) {
                log.warn("FCM_HIZMET_HESABI tanımlı değil; push bildirimleri gönderilmeyecek, yalnızca uygulama içinde kalacak.")
                return null
            }
            return FcmPushGonderici(hesap)
        }
    }
}
