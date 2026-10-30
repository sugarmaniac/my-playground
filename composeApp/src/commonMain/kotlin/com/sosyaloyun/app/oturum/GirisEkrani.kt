package com.sosyaloyun.app.oturum

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sosyaloyun.app.itiraz.ItirazFormu
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.itiraz_bekliyor
import com.sosyaloyun.app.resources.app_name
import com.sosyaloyun.app.resources.giris_apple
import com.sosyaloyun.app.resources.giris_google
import com.sosyaloyun.app.resources.giris_hata_askida
import com.sosyaloyun.app.resources.giris_hata_baglanti
import com.sosyaloyun.app.resources.giris_hata_banli
import com.sosyaloyun.app.resources.giris_hata_reddedildi
import com.sosyaloyun.app.resources.giris_hata_saglayici
import com.sosyaloyun.app.resources.giris_saglayici_yok
import com.sosyaloyun.shared.KimlikSaglayici
import org.jetbrains.compose.resources.stringResource

@Composable
fun GirisEkraniGorunumu(
    ekran: OturumEkrani.Giris,
    saglayicilar: List<KimlikSaglayici>,
    girisYap: (KimlikSaglayici) -> Unit,
    itirazMetniDegisti: (String) -> Unit = {},
    itirazGonder: () -> Unit = {},
) {
    Column(
        // İtiraz formu klavyeyle birlikte ekrana sığmayabilir; kaydırılır, kısa içerik yine ortada durur.
        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineMedium)
        if (ekran.yukleniyor) {
            CircularProgressIndicator()
            return@Column
        }
        if (saglayicilar.isEmpty()) Text(stringResource(Res.string.giris_saglayici_yok))
        saglayicilar.forEach { saglayici ->
            Button(onClick = { girisYap(saglayici) }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        when (saglayici) {
                            KimlikSaglayici.APPLE -> Res.string.giris_apple
                            KimlikSaglayici.GOOGLE -> Res.string.giris_google
                        },
                    ),
                )
            }
        }
        ekran.hata?.let { hata ->
            Text(
                when (hata) {
                    GirisHatasi.SAGLAYICI -> stringResource(Res.string.giris_hata_saglayici)
                    GirisHatasi.REDDEDILDI -> stringResource(Res.string.giris_hata_reddedildi)
                    GirisHatasi.BAGLANTI -> stringResource(Res.string.giris_hata_baglanti)
                    GirisHatasi.HESAP_ASKIDA -> stringResource(Res.string.giris_hata_askida, ekran.askiBitisi.orEmpty())
                    GirisHatasi.HESAP_BANLI -> stringResource(Res.string.giris_hata_banli)
                },
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (ekran.itirazAnahtari != null) {
            if (ekran.itiraz == GirisItirazi.GONDERILDI) {
                Text(stringResource(Res.string.itiraz_bekliyor))
            } else {
                ItirazFormu(ekran.itirazMetni, ekran.itiraz == GirisItirazi.HATA, ekran.itirazGonderilebilir, itirazMetniDegisti, itirazGonder)
            }
        }
    }
}
