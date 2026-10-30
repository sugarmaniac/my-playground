package com.sosyaloyun.app.sosyal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sosyaloyun.app.api.EngelListesiTuru
import com.sosyaloyun.app.api.KisiListesiTuru
import com.sosyaloyun.app.arama.KullaniciSatiri
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.arama_daha_fazla
import com.sosyaloyun.app.resources.arkadaslar
import com.sosyaloyun.app.resources.arkadaslik_istekleri
import com.sosyaloyun.app.resources.kabul_et
import com.sosyaloyun.app.resources.reddet
import com.sosyaloyun.app.resources.durum_hata
import com.sosyaloyun.app.resources.engeli_kaldir
import com.sosyaloyun.app.resources.engellenenler
import com.sosyaloyun.app.resources.sessize_alinanlar
import com.sosyaloyun.app.resources.sessizden_cikar
import com.sosyaloyun.app.resources.geri
import com.sosyaloyun.app.resources.profil_bos_sekme
import com.sosyaloyun.app.resources.takip_edilenler
import com.sosyaloyun.app.resources.takipciler
import org.jetbrains.compose.resources.stringResource

@Composable
fun TakipListesiEkrani(viewModel: TakipListesiViewModel, tur: KisiListesiTuru, kullaniciyaGit: (String) -> Unit, geri: () -> Unit) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    KisiListesi(
        baslik = stringResource(
            when (tur) {
                KisiListesiTuru.TAKIPCILER -> Res.string.takipciler
                KisiListesiTuru.TAKIP_EDILENLER -> Res.string.takip_edilenler
                KisiListesiTuru.ARKADASLAR -> Res.string.arkadaslar
            },
        ),
        ekran = ekran,
        kullaniciyaGit = kullaniciyaGit,
        dahaFazla = viewModel::dahaFazlaYukle,
        geri = geri,
    )
}

/** Ayarlar: engellenenler ya da sessize alınanlar; her kişinin yanında geri alma. Engellenenin profili açılmaz. */
@Composable
fun EngelListesiEkrani(viewModel: EngelListesiViewModel, tur: EngelListesiTuru, kullaniciyaGit: (String) -> Unit, geri: () -> Unit) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    KisiListesi(
        baslik = stringResource(if (tur == EngelListesiTuru.ENGELLENENLER) Res.string.engellenenler else Res.string.sessize_alinanlar),
        ekran = ekran,
        kullaniciyaGit = if (tur == EngelListesiTuru.ENGELLENENLER) ({}) else kullaniciyaGit,
        dahaFazla = viewModel::dahaFazlaYukle,
        geri = geri,
        islemler = { ad ->
            TextButton(onClick = { viewModel.kaldir(ad) }) {
                Text(stringResource(if (tur == EngelListesiTuru.ENGELLENENLER) Res.string.engeli_kaldir else Res.string.sessizden_cikar))
            }
        },
    )
}

/** Gelen arkadaşlık istekleri: her kişinin yanında kabul ve ret. */
@Composable
fun GelenIsteklerEkrani(viewModel: GelenIsteklerViewModel, kullaniciyaGit: (String) -> Unit, geri: () -> Unit) {
    val ekran by viewModel.ekran.collectAsStateWithLifecycle()
    KisiListesi(
        baslik = stringResource(Res.string.arkadaslik_istekleri),
        ekran = ekran,
        kullaniciyaGit = kullaniciyaGit,
        dahaFazla = {},
        geri = geri,
        islemler = { ad ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { viewModel.kabulEt(ad) }) { Text(stringResource(Res.string.kabul_et)) }
                TextButton(onClick = { viewModel.reddet(ad) }) { Text(stringResource(Res.string.reddet)) }
            }
        },
    )
}

@Composable
private fun KisiListesi(
    baslik: String,
    ekran: KullaniciListesiEkrani,
    kullaniciyaGit: (String) -> Unit,
    dahaFazla: () -> Unit,
    geri: () -> Unit,
    islemler: (@Composable (String) -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = geri) { Text(stringResource(Res.string.geri)) }
        Text(baslik, style = MaterialTheme.typography.headlineSmall)
        when (val durum = ekran) {
            KullaniciListesiEkrani.Yukleniyor -> CircularProgressIndicator()
            KullaniciListesiEkrani.Hata -> Text(stringResource(Res.string.durum_hata))
            is KullaniciListesiEkrani.Hazir -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (durum.kullanicilar.isEmpty()) item { Text(stringResource(Res.string.profil_bos_sekme)) }
                items(durum.kullanicilar, key = { it.kullaniciId }) { kisi ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { KullaniciSatiri(kisi) { kullaniciyaGit(kisi.kullaniciAdi) } }
                        islemler?.invoke(kisi.kullaniciAdi)
                    }
                }
                if (durum.dahaVar) {
                    item {
                        TextButton(onClick = dahaFazla, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(Res.string.arama_daha_fazla))
                        }
                    }
                }
            }
        }
    }
}
