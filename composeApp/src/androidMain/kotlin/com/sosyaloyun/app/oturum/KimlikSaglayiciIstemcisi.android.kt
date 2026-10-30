package com.sosyaloyun.app.oturum

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.sosyaloyun.app.api.DerlemeAyarlari
import com.sosyaloyun.shared.KimlikSaglayici

/** Android'de Google ile giriş Credential Manager üzerinden; Apple ile giriş Android'de sunulmaz. */
private class AndroidKimlikSaglayiciIstemcisi(private val context: Context) : KimlikSaglayiciIstemcisi {
    private val istemciKimligi = DerlemeAyarlari.GOOGLE_SUNUCU_ISTEMCI_KIMLIGI

    override val desteklenenler: List<KimlikSaglayici> =
        if (istemciKimligi.isNotBlank()) listOf(KimlikSaglayici.GOOGLE) else emptyList()

    override suspend fun kimlikTokeniAl(saglayici: KimlikSaglayici): KimlikTokeniSonucu {
        if (saglayici != KimlikSaglayici.GOOGLE || istemciKimligi.isBlank()) return KimlikTokeniSonucu.Hata
        val istek = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(istemciKimligi).build())
            .build()
        return try {
            val kimlik = CredentialManager.create(context).getCredential(context, istek).credential
            if (kimlik is CustomCredential && kimlik.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                KimlikTokeniSonucu.Token(GoogleIdTokenCredential.createFrom(kimlik.data).idToken)
            } else {
                KimlikTokeniSonucu.Hata
            }
        } catch (e: GetCredentialCancellationException) {
            KimlikTokeniSonucu.Iptal
        } catch (e: GetCredentialException) {
            KimlikTokeniSonucu.Hata
        } catch (e: GoogleIdTokenParsingException) {
            KimlikTokeniSonucu.Hata
        }
    }
}

@Composable
actual fun kimlikSaglayiciIstemcisi(): KimlikSaglayiciIstemcisi {
    val context = LocalContext.current
    return remember(context) { AndroidKimlikSaglayiciIstemcisi(context) }
}
