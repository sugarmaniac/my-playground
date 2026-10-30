package com.sosyaloyun.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.sosyaloyun.app.akis.AkisEkrani
import com.sosyaloyun.app.akis.AkisViewModel
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.api.EngelListesiTuru
import com.sosyaloyun.app.api.KisiListesiTuru
import com.sosyaloyun.app.arama.AramaEkrani
import com.sosyaloyun.app.arama.AramaViewModel
import com.sosyaloyun.app.api.KtorApiIstemcisi
import com.sosyaloyun.app.gunluk.DetayliKayitEkrani
import com.sosyaloyun.app.gunluk.DetayliKayitViewModel
import com.sosyaloyun.app.gunluk.GunlukDetayGorunumu
import com.sosyaloyun.app.gunluk.GunlukDetayViewModel
import com.sosyaloyun.app.hesap.AnaEkran
import com.sosyaloyun.app.hesap.DavetKodlarimViewModel
import com.sosyaloyun.app.hesap.DavetKoduEkrani
import com.sosyaloyun.app.hesap.DavetKoduViewModel
import com.sosyaloyun.app.hesap.HataGorunumu
import com.sosyaloyun.app.hesap.ProfilEkrani
import com.sosyaloyun.app.hesap.HesapEkrani
import com.sosyaloyun.app.hesap.HesapViewModel
import com.sosyaloyun.app.hesap.ProfilEkrani
import com.sosyaloyun.app.hesap.ProfilViewModel
import com.sosyaloyun.app.hesap.YukleniyorGorunumu
import com.sosyaloyun.app.oturum.AyarliOturumDeposu
import com.sosyaloyun.app.oyun.OyunDetayEkrani
import com.sosyaloyun.app.oyun.OyunDetayViewModel
import com.sosyaloyun.app.profil.ProfilSayfasiEkrani
import com.sosyaloyun.app.profil.ProfilSayfasiViewModel
import com.sosyaloyun.app.profil.VitrinDuzenleEkrani
import com.sosyaloyun.app.profil.VitrinViewModel
import com.sosyaloyun.app.sosyal.EngelListesiEkrani
import com.sosyaloyun.app.sosyal.EngelListesiViewModel
import com.sosyaloyun.app.sosyal.GelenIsteklerEkrani
import com.sosyaloyun.app.sosyal.GelenIsteklerViewModel
import com.sosyaloyun.app.sosyal.TakipListesiEkrani
import com.sosyaloyun.app.sosyal.TakipListesiViewModel
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.app.resources.akis_ara
import com.sosyaloyun.app.resources.akis_baslik
import com.sosyaloyun.app.resources.akis_davetler
import com.sosyaloyun.app.resources.akis_istekler
import com.sosyaloyun.app.resources.sekme_ana
import com.sosyaloyun.app.resources.sekme_kaydet
import com.sosyaloyun.app.resources.sekme_profil
import com.sosyaloyun.app.oturum.GirisEkraniGorunumu
import com.sosyaloyun.app.oturum.OturumEkrani
import com.sosyaloyun.app.oturum.OturumViewModel
import com.sosyaloyun.app.oturum.kimlikSaglayiciIstemcisi
import com.sosyaloyun.shared.ApiJson
import com.sosyaloyun.shared.KullaniciOzeti
import com.sosyaloyun.shared.OyunOzeti
import com.sosyaloyun.shared.ProfilSayfasi
import org.jetbrains.compose.resources.stringResource
import kotlinx.serialization.Serializable

@Composable
fun App() {
    val depo = remember { AyarliOturumDeposu() }
    val api = remember { KtorApiIstemcisi(depo) }
    val oturum = viewModel { OturumViewModel(api, depo) }
    val saglayiciIstemcisi = kimlikSaglayiciIstemcisi()
    val ekran by oturum.ekran.collectAsStateWithLifecycle()

    setSingletonImageLoaderFactory { baglam ->
        ImageLoader.Builder(baglam).components { add(KtorNetworkFetcherFactory()) }.build()
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val durum = ekran) {
                is OturumEkrani.Giris -> GirisEkraniGorunumu(
                    ekran = durum,
                    saglayicilar = saglayiciIstemcisi.desteklenenler,
                    girisYap = { saglayici -> oturum.girisYap(saglayici) { saglayiciIstemcisi.kimlikTokeniAl(saglayici) } },
                )
                OturumEkrani.Acik -> HesapAkisi(api, cikisYap = oturum::cikisYap)
            }
        }
    }
}

/** Girişten sonra onboarding adımları (davet kodu, kullanıcı adı) ve ardından ana ekran. */
@Composable
private fun HesapAkisi(api: ApiIstemcisi, cikisYap: () -> Unit) {
    val hesap = viewModel { HesapViewModel(api) }
    val ekran by hesap.ekran.collectAsStateWithLifecycle()
    when (val durum = ekran) {
        HesapEkrani.Yukleniyor -> YukleniyorGorunumu()
        HesapEkrani.Hata -> HataGorunumu(tekrarDene = hesap::yenile)
        HesapEkrani.OturumGecersiz -> LaunchedEffect(Unit) { cikisYap() }
        HesapEkrani.DavetKodu -> DavetKoduEkrani(viewModel { DavetKoduViewModel(api, hesabiYenile = hesap::yenile) })
        HesapEkrani.Profil -> ProfilEkrani(viewModel { ProfilViewModel(api, hesabiYenile = hesap::yenile) })
        is HesapEkrani.Hazir -> UygulamaGezintisi(api, durum.profil, cikisYap)
    }
}

@Serializable
private data object AnaRota

@Serializable
private data object ProfilRota

/** [mod]: oyuna gitmek, ortadaki kaydet butonundan hızlı kayıt ya da vitrine oyun seçmek için arama. */
@Serializable
private data class AramaRota(val mod: AramaModu = AramaModu.GEZINTI)

@Serializable
enum class AramaModu { GEZINTI, KAYIT, VITRIN }

@Serializable
private data class OyunRota(val oyunId: String, val hizliKayitAc: Boolean = false)

@Serializable
private data class DetayliKayitRota(val oyunId: String, val kayitId: String? = null)

@Serializable
private data class GunlukDetayRota(val kayitId: String)

@Serializable
private data class KullaniciRota(val kullaniciAdi: String)

@Serializable
private data class TakipListesiRota(val kullaniciAdi: String, val tur: KisiListesiTuru)

@Serializable
private data class EngelListesiRota(val tur: EngelListesiTuru)

@Serializable
private data object GelenIsteklerRota

@Serializable
private data object DavetlerRota

@Serializable
private data object ProfilDuzenleRota

@Serializable
private data object VitrinRota

/** Alt ekranlardan dönünce önceki ekranın yeniden yüklenmesi ya da seçilen oyunu alması için bırakılan işaretler. */
private const val GUNLUK_DEGISTI = "gunlukDegisti"
private const val PROFIL_DEGISTI = "profilDegisti"
private const val VITRIN_OYUNU = "vitrinOyunu"

/** Onboarding bittikten sonraki ekranlar: alt gezinme çubuğu (Ana sayfa, ortada Kaydet, Profil). */
@Composable
private fun UygulamaGezintisi(api: ApiIstemcisi, profil: KullaniciOzeti, cikisYap: () -> Unit) {
    val gezinti = rememberNavController()
    val girdi by gezinti.currentBackStackEntryAsState()
    val hedef = girdi?.destination
    fun sekmeyeGit(rota: Any) = gezinti.navigate(rota) {
        popUpTo(gezinti.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    // Profil sayfası ve düzenleme ekranları aynı ViewModel'leri paylaşmasın diye profil verisi rotalar arasında taşınır.
    var duzenlenenProfil by remember { mutableStateOf<ProfilSayfasi?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = hedef?.hierarchy?.any { it.hasRoute<AnaRota>() } == true,
                    onClick = { sekmeyeGit(AnaRota) },
                    icon = { Text("🏠") },
                    label = { Text(stringResource(Res.string.sekme_ana)) },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { gezinti.navigate(AramaRota(AramaModu.KAYIT)) },
                    icon = { Text("➕") },
                    label = { Text(stringResource(Res.string.sekme_kaydet)) },
                )
                NavigationBarItem(
                    selected = hedef?.hierarchy?.any { it.hasRoute<ProfilRota>() } == true,
                    onClick = { sekmeyeGit(ProfilRota) },
                    icon = { Text("👤") },
                    label = { Text(stringResource(Res.string.sekme_profil)) },
                )
            }
        },
    ) { bosluk ->
        NavHost(gezinti, startDestination = AnaRota, modifier = Modifier.padding(bosluk)) {
            composable<AnaRota> {
                AkisEkrani(
                    viewModel { AkisViewModel(api) },
                    ustCubuk = {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(Res.string.akis_baslik), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { gezinti.navigate(AramaRota()) }) { Text(stringResource(Res.string.akis_ara)) }
                            TextButton(onClick = { gezinti.navigate(GelenIsteklerRota) }) { Text(stringResource(Res.string.akis_istekler)) }
                            TextButton(onClick = { gezinti.navigate(DavetlerRota) }) { Text(stringResource(Res.string.akis_davetler)) }
                        }
                    },
                    oyunaGit = { gezinti.navigate(OyunRota(it)) },
                    kullaniciyaGit = { gezinti.navigate(KullaniciRota(it)) },
                    gunlukKaydinaGit = { gezinti.navigate(GunlukDetayRota(it)) },
                )
            }
            composable<GunlukDetayRota> { detayGirdisi ->
                val kayitId = detayGirdisi.toRoute<GunlukDetayRota>().kayitId
                GunlukDetayGorunumu(
                    viewModel(key = "gunluk-detay-$kayitId") { GunlukDetayViewModel(api, kayitId) },
                    oyunaGit = { gezinti.navigate(OyunRota(it)) },
                    kullaniciyaGit = { gezinti.navigate(KullaniciRota(it)) },
                    geri = { gezinti.popBackStack() },
                )
            }
            composable<DavetlerRota> {
                AnaEkran(
                    profil = profil,
                    davetKodlari = viewModel { DavetKodlarimViewModel(api) },
                    geri = { gezinti.popBackStack() },
                    engelListesi = { gezinti.navigate(EngelListesiRota(it)) },
                    cikisYap = cikisYap,
                )
            }
            composable<EngelListesiRota> { listeGirdisi ->
                val tur = listeGirdisi.toRoute<EngelListesiRota>().tur
                EngelListesiEkrani(
                    viewModel(key = "engel-$tur") { EngelListesiViewModel(api, tur) },
                    tur = tur,
                    kullaniciyaGit = { gezinti.navigate(KullaniciRota(it)) },
                    geri = { gezinti.popBackStack() },
                )
            }
            composable<ProfilRota> { profilGirdisi ->
                val profilVm = viewModel { ProfilSayfasiViewModel(api) }
                val degisti by profilGirdisi.savedStateHandle.getStateFlow(PROFIL_DEGISTI, false).collectAsStateWithLifecycle()
                LaunchedEffect(degisti) {
                    if (degisti) {
                        profilGirdisi.savedStateHandle[PROFIL_DEGISTI] = false
                        profilVm.yenile()
                    }
                }
                ProfilSayfasiEkrani(
                    profilVm,
                    profiliDuzenle = {
                        duzenlenenProfil = it
                        gezinti.navigate(ProfilDuzenleRota)
                    },
                    vitriniDuzenle = {
                        duzenlenenProfil = it
                        gezinti.navigate(VitrinRota)
                    },
                    oyunaGit = { gezinti.navigate(OyunRota(it)) },
                    kisiListesi = { ad, tur -> gezinti.navigate(TakipListesiRota(ad, tur)) },
                    gunlukKaydinaGit = { gezinti.navigate(GunlukDetayRota(it)) },
                )
            }
            composable<KullaniciRota> { kullaniciGirdisi ->
                val ad = kullaniciGirdisi.toRoute<KullaniciRota>().kullaniciAdi
                ProfilSayfasiEkrani(
                    viewModel(key = "profil-$ad") { ProfilSayfasiViewModel(api, if (ad == profil.kullaniciAdi) null else ad) },
                    profiliDuzenle = {
                        duzenlenenProfil = it
                        gezinti.navigate(ProfilDuzenleRota)
                    },
                    vitriniDuzenle = {
                        duzenlenenProfil = it
                        gezinti.navigate(VitrinRota)
                    },
                    oyunaGit = { gezinti.navigate(OyunRota(it)) },
                    kisiListesi = { kisi, tur -> gezinti.navigate(TakipListesiRota(kisi, tur)) },
                    gunlukKaydinaGit = { gezinti.navigate(GunlukDetayRota(it)) },
                    geri = { gezinti.popBackStack() },
                )
            }
            composable<TakipListesiRota> { listeGirdisi ->
                val rota = listeGirdisi.toRoute<TakipListesiRota>()
                TakipListesiEkrani(
                    viewModel(key = "kisiler-${rota.kullaniciAdi}-${rota.tur}") { TakipListesiViewModel(api, rota.kullaniciAdi, rota.tur) },
                    tur = rota.tur,
                    kullaniciyaGit = { gezinti.navigate(KullaniciRota(it)) },
                    geri = { gezinti.popBackStack() },
                )
            }
            composable<GelenIsteklerRota> {
                GelenIsteklerEkrani(
                    viewModel { GelenIsteklerViewModel(api) },
                    kullaniciyaGit = { gezinti.navigate(KullaniciRota(it)) },
                    geri = { gezinti.popBackStack() },
                )
            }
            composable<AramaRota> { aramaGirdisi ->
                val mod = aramaGirdisi.toRoute<AramaRota>().mod
                AramaEkrani(
                    viewModel(key = "arama-$mod") { AramaViewModel(api) },
                    geri = { gezinti.popBackStack() },
                    oyunSecildi = { oyun ->
                        when (mod) {
                            AramaModu.GEZINTI -> gezinti.navigate(OyunRota(oyun.id))
                            AramaModu.KAYIT -> gezinti.navigate(OyunRota(oyun.id, hizliKayitAc = true)) {
                                popUpTo<AramaRota> { inclusive = true }
                            }
                            AramaModu.VITRIN -> {
                                gezinti.previousBackStackEntry?.savedStateHandle?.set(VITRIN_OYUNU, ApiJson.encodeToString(OyunOzeti.serializer(), oyun))
                                gezinti.popBackStack()
                            }
                        }
                    },
                    kullaniciSecildi = if (mod == AramaModu.GEZINTI) ({ gezinti.navigate(KullaniciRota(it)) }) else null,
                )
            }
            composable<OyunRota> { oyunGirdisi ->
                val rota = oyunGirdisi.toRoute<OyunRota>()
                val detay = viewModel(key = rota.oyunId) { OyunDetayViewModel(api, rota.oyunId) }
                val gunlukDegisti by oyunGirdisi.savedStateHandle.getStateFlow(GUNLUK_DEGISTI, false).collectAsStateWithLifecycle()
                LaunchedEffect(gunlukDegisti) {
                    if (gunlukDegisti) {
                        oyunGirdisi.savedStateHandle[GUNLUK_DEGISTI] = false
                        detay.yukle()
                    }
                }
                OyunDetayEkrani(
                    detay,
                    api = api,
                    geri = { gezinti.popBackStack() },
                    oyunaGit = { gezinti.navigate(OyunRota(it)) },
                    gunlugeYaz = { kayitId -> gezinti.navigate(DetayliKayitRota(rota.oyunId, kayitId)) },
                    kullaniciyaGit = { gezinti.navigate(KullaniciRota(it)) },
                    gunlukKaydinaGit = { gezinti.navigate(GunlukDetayRota(it)) },
                    hizliKayitAcik = rota.hizliKayitAc,
                )
            }
            composable<DetayliKayitRota> { kayitGirdisi ->
                val rota = kayitGirdisi.toRoute<DetayliKayitRota>()
                DetayliKayitEkrani(
                    viewModel(key = "gunluk-${rota.oyunId}-${rota.kayitId}") {
                        DetayliKayitViewModel(api, rota.oyunId, rota.kayitId) {
                            gezinti.previousBackStackEntry?.savedStateHandle?.set(GUNLUK_DEGISTI, true)
                            gezinti.popBackStack()
                        }
                    },
                    geri = { gezinti.popBackStack() },
                )
            }
            composable<ProfilDuzenleRota> {
                val mevcut = duzenlenenProfil
                if (mevcut == null) {
                    LaunchedEffect(Unit) { gezinti.popBackStack() }
                    return@composable
                }
                ProfilEkrani(
                    viewModel {
                        ProfilViewModel(api, mevcut.kullanici, mevcut.biyografi) {
                            gezinti.previousBackStackEntry?.savedStateHandle?.set(PROFIL_DEGISTI, true)
                            gezinti.popBackStack()
                        }
                    },
                )
            }
            composable<VitrinRota> { vitrinGirdisi ->
                val mevcut = duzenlenenProfil
                if (mevcut == null) {
                    LaunchedEffect(Unit) { gezinti.popBackStack() }
                    return@composable
                }
                val vitrinVm = viewModel {
                    VitrinViewModel(api, mevcut.vitrin) {
                        gezinti.previousBackStackEntry?.savedStateHandle?.set(PROFIL_DEGISTI, true)
                        gezinti.popBackStack()
                    }
                }
                val secilen by vitrinGirdisi.savedStateHandle.getStateFlow<String?>(VITRIN_OYUNU, null).collectAsStateWithLifecycle()
                LaunchedEffect(secilen) {
                    secilen?.let {
                        vitrinGirdisi.savedStateHandle[VITRIN_OYUNU] = null
                        vitrinVm.ekle(ApiJson.decodeFromString(OyunOzeti.serializer(), it))
                    }
                }
                VitrinDuzenleEkrani(vitrinVm, oyunEkle = { gezinti.navigate(AramaRota(AramaModu.VITRIN)) }, geri = { gezinti.popBackStack() })
            }
        }
    }
}
