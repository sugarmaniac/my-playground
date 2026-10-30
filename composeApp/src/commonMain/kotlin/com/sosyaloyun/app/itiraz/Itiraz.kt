package com.sosyaloyun.app.itiraz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.itiraz_baslik
import com.sosyaloyun.app.resources.itiraz_bekliyor
import com.sosyaloyun.app.resources.itiraz_et
import com.sosyaloyun.app.resources.itiraz_hata
import com.sosyaloyun.app.resources.itiraz_kabul
import com.sosyaloyun.app.resources.itiraz_metni
import com.sosyaloyun.app.resources.itiraz_red
import com.sosyaloyun.app.resources.yaptirim_aski
import com.sosyaloyun.app.resources.yaptirim_ban
import com.sosyaloyun.app.resources.yaptirim_icerik
import com.sosyaloyun.app.resources.yaptirim_uyari
import com.sosyaloyun.app.resources.yaptirim_yorum_yasagi
import com.sosyaloyun.shared.ItirazDurumu
import com.sosyaloyun.shared.ItirazKurallari
import com.sosyaloyun.shared.ItirazSonucu
import com.sosyaloyun.shared.YaptirimTuru
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Ayarlardaki itiraz bölümü (Faz 2, #53); itiraz edilecek yaptırım yoksa ya da yüklenemezse gösterilmez. */
data class ItirazEkrani(val durum: ItirazDurumu? = null, val metin: String = "", val gonderiliyor: Boolean = false, val hata: Boolean = false) {
    val gonderilebilir: Boolean
        get() = durum?.itirazEdilebilir == true && !gonderiliyor && metin.isNotBlank() && metin.trim().length <= ItirazKurallari.EN_FAZLA
}

class ItirazViewModel(private val api: ApiIstemcisi) : ViewModel() {
    private val _ekran = MutableStateFlow(ItirazEkrani())
    val ekran: StateFlow<ItirazEkrani> = _ekran

    init {
        viewModelScope.launch {
            try {
                _ekran.value = ItirazEkrani(api.itirazDurumu())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Bölüm gösterilmez.
            }
        }
    }

    fun metinDegisti(metin: String) = _ekran.update { it.copy(metin = metin, hata = false) }

    fun gonder() {
        val ekran = _ekran.value
        if (!ekran.gonderilebilir) return
        _ekran.value = ekran.copy(gonderiliyor = true, hata = false)
        viewModelScope.launch {
            try {
                _ekran.value = ItirazEkrani(api.itirazEt(ekran.metin.trim()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ekran.update { it.copy(gonderiliyor = false, hata = true) }
            }
        }
    }
}

@Composable
fun ItirazBolumu(viewModel: ItirazViewModel) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    val durum = ekran.durum ?: return
    val yaptirim = durum.yaptirim ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.itiraz_baslik), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(yaptirimAdi(yaptirim.tur)), style = MaterialTheme.typography.bodySmall)
        when (durum.itiraz) {
            ItirazSonucu.BEKLIYOR -> Text(stringResource(Res.string.itiraz_bekliyor))
            ItirazSonucu.KABUL_EDILDI -> Text(stringResource(Res.string.itiraz_kabul))
            ItirazSonucu.REDDEDILDI -> Text(stringResource(Res.string.itiraz_red))
            null -> ItirazFormu(ekran.metin, ekran.hata, ekran.gonderilebilir, viewModel::metinDegisti, viewModel::gonder)
        }
    }
}

/** Ayarlarda ve giriş ekranında ortak itiraz alanı. */
@Composable
fun ItirazFormu(metin: String, hata: Boolean, gonderilebilir: Boolean, metinDegisti: (String) -> Unit, gonder: () -> Unit) {
    OutlinedTextField(metin, metinDegisti, label = { Text(stringResource(Res.string.itiraz_metni)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    if (hata) Text(stringResource(Res.string.itiraz_hata), color = MaterialTheme.colorScheme.error)
    Button(onClick = gonder, enabled = gonderilebilir) { Text(stringResource(Res.string.itiraz_et)) }
}

private fun yaptirimAdi(tur: YaptirimTuru) = when (tur) {
    YaptirimTuru.ICERIK_KALDIRMA -> Res.string.yaptirim_icerik
    YaptirimTuru.ASKI -> Res.string.yaptirim_aski
    YaptirimTuru.BAN -> Res.string.yaptirim_ban
    YaptirimTuru.UYARI -> Res.string.yaptirim_uyari
    YaptirimTuru.YORUM_YASAGI -> Res.string.yaptirim_yorum_yasagi
}
