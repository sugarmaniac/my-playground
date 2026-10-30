package com.sosyaloyun.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.sosyaloyun.app.liste.OyunListeleriViewModel
import com.sosyaloyun.app.liste.OyunListeleriBolumu
import com.sosyaloyun.app.liste.ListeDuzenleViewModel
import com.sosyaloyun.app.liste.ListeDuzenleEkrani
import com.sosyaloyun.app.liste.ListeDetayViewModel
import com.sosyaloyun.app.liste.ListeDetayEkrani
import com.sosyaloyun.app.akis.AkisEkrani
import com.sosyaloyun.app.akis.AkisViewModel
import com.sosyaloyun.app.api.ApiIstemcisi
import com.sosyaloyun.app.api.EngelListesiTuru
import com.sosyaloyun.app.sikayet.SikayetHedefi
import com.sosyaloyun.app.sikayet.SikayetPenceresi
import com.sosyaloyun.app.sikayet.SikayetViewModel
import com.sosyaloyun.app.bildirim.BildirimTercihleriViewModel
import com.sosyaloyun.app.bildirim.BildirimlerGorunumu
import com.sosyaloyun.app.bildirim.BildirimlerViewModel
import com.sosyaloyun.app.bildirim.OkunmamisBildirimViewModel
import com.sosyaloyun.app.resources.akis_bildirimler
import com.sosyaloyun.app.kesif.KesifEkrani
import com.sosyaloyun.app.kesif.KesifViewModel
import com.sosyaloyun.app.kesif.OyunListesiGorunumu
import com.sosyaloyun.app.kesif.OyunListesiViewModel
import com.sosyaloyun.app.kesif.turAdi
import com.sosyaloyun.app.resources.sekme_kesif
import com.sosyaloyun.shared.Liste
import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.Tur
import com.sosyaloyun.app.api.KisiListesiTuru
import com.sosyaloyun.app.arama.AramaEkrani
import com.sosyaloyun.app.arama.AramaViewModel
import com.sosyaloyun.app.api.KtorApiIstemcisi
import com.sosyaloyun.app.gunluk.DetayliKayitEkrani
import com.sosyaloyun.app.gunluk.DetayliKayitViewModel
import com.sosyaloyun.app.gunluk.GunlukDetayGorunumu
import com.sosyaloyun.app.gunluk.GunlukDetayViewModel
import com.sosyaloyun.app.gizlilik.GizlilikViewModel
import com.sosyaloyun.app.kurator.KuratorListeleriBolumu
import com.sosyaloyun.app.kurator.KuratorListeleriViewModel
import com.sosyaloyun.app.istatistik.YillikOzetGorunumu
import com.sosyaloyun.app.istatistik.YillikOzetViewModel
import com.sosyaloyun.app.itiraz.ItirazViewModel
import com.sosyaloyun.app.kurator.KuratorViewModel
import com.sosyaloyun.app.oneri.OnerilerBolumu
import com.sosyaloyun.app.oneri.OnerilerViewModel
import com.sosyaloyun.app.puan.PuanDegisimiBolumu
import com.sosyaloyun.app.puan.PuanDegisimiViewModel
import com.sosyaloyun.app.puan.PuanGecmisiBolumu
import com.sosyaloyun.app.puan.PuanGecmisiViewModel
import com.sosyaloyun.app.hesap.AnaEkran
import com.sosyaloyun.app.hesap.DavetKodlarimViewModel
import com.sosyaloyun.app.hesap.DavetKoduEkrani
import com.sosyaloyun.app.hesap.DavetKoduViewModel
import com.sosyaloyun.app.hesap.HataGorunumu
import com.sosyaloyun.app.hesap.ProfilEkrani
import com.sosyaloyun.app.hesap.HesapEkrani
import com.sosyaloyun.app.hesap.HesapSilmeViewModel
import com.sosyaloyun.app.hesap.HesapViewModel
import com.sosyaloyun.app.hesap.ProfilEkrani
import com.sosyaloyun.app.hesap.ProfilViewModel
import com.sosyaloyun.app.hesap.YukleniyorGorunumu
import com.sosyaloyun.app.onboarding.OnboardingEkrani
import com.sosyaloyun.app.onboarding.OnboardingViewModel
import com.sosyaloyun.app.onboarding.SteamAyariViewModel
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
                    itirazMetniDegisti = oturum::itirazMetniDegisti,
                    itirazGonder = oturum::itirazGonder,
                )
                OturumEkrani.Acik -> HesapAkisi(api, cikisYap = oturum::cikisYap, hesapSilindi = oturum::hesapSilindi)
            }
        }
    }
}

/** Girişten sonra onboarding adımları (davet kodu, kullanıcı adı) ve ardından ana ekran. */
@Composable
private fun HesapAkisi(api: ApiIstemcisi, cikisYap: () -> Unit, hesapSilindi: () -> Unit) {
    val hesap = viewModel { HesapViewModel(api) }
    val ekran by hesap.ekran.collectAsStateWithLifecycle()
    when (val durum = ekran) {
        HesapEkrani.Yukleniyor -> YukleniyorGorunumu()
        HesapEkrani.Hata -> HataGorunumu(tekrarDene = hesap::yenile)
        HesapEkrani.OturumGecersiz -> LaunchedEffect(Unit) { cikisYap() }
        HesapEkrani.DavetKodu -> DavetKoduEkrani(viewModel { DavetKoduViewModel(api, hesabiYenile = hesap::yenile) })
        HesapEkrani.Profil -> ProfilEkrani(viewModel { ProfilViewModel(api, hesabiYenile = hesap::yenile) })
        HesapEkrani.Onboarding -> OnboardingEkrani(viewModel { OnboardingViewModel(api, hesabiYenile = hesap::yenile) })
        is HesapEkrani.Hazir -> CompositionLocalProvider(LocalAktifFaz provides durum.aktifFaz) {
            UygulamaGezintisi(api, durum.profil, cikisYap, hesapSilindi)
        }
    }
}

@Serializable
private data object AnaRota

@Serializable
private data object ProfilRota

@Serializable
private data object KesifRota

/** Keşif kısayolu: [tur] ya da [platform] dolu. */
@Serializable
private data class OyunListesiRota(val tur: Tur? = null, val platform: String? = null)

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
private data object BildirimlerRota

@Serializable
private data object GelenIsteklerRota

@Serializable
private data object DavetlerRota

@Serializable
private data object ProfilDuzenleRota

@Serializable
private data object VitrinRota

@Serializable
private data class ListeRota(val listeId: String)

/** Faz 3 (#55): yıllık özet; [kullaniciAdi] null ise kendi özetin. */
@Serializable
private data class YillikOzetRota(val kullaniciAdi: String?, val yil: Int)

/** Düzenlenen liste [duzenlenenListe]'de; null ise yeni liste. */
@Serializable
private data object ListeDuzenleRota

/** Alt ekranlardan dönünce önceki ekranın yeniden yüklenmesi ya da seçilen oyunu alması için bırakılan işaretler. */
private const val GUNLUK_DEGISTI = "gunlukDegisti"
private const val PROFIL_DEGISTI = "profilDegisti"
private const val VITRIN_OYUNU = "vitrinOyunu"

/** Onboarding bittikten sonraki ekranlar: alt gezinme çubuğu (Ana sayfa, ortada Kaydet, Profil). */
@Composable
private fun UygulamaGezintisi(api: ApiIstemcisi, profil: KullaniciOzeti, cikisYap: () -> Unit, hesapSilindi: () -> Unit) {
    val aktifFaz = LocalAktifFaz.current
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
    var duzenlenenListe by remember { mutableStateOf<Liste?>(null) }
    var guncellenenListe by remember { mutableStateOf<Liste?>(null) }

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
                    selected = hedef?.hierarchy?.any { it.hasRoute<KesifRota>() } == true,
                    onClick = { sekmeyeGit(KesifRota) },
                    icon = { Text("🧭") },
                    label = { Text(stringResource(Res.string.sekme_kesif)) },
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
                val bildirimSayaci = viewModel { OkunmamisBildirimViewModel(api) }
                val okunmamis by bildirimSayaci.sayi.collectAsStateWithLifecycle()
                // Akışa her dönüşte (ör. bildirimler ekranından) sayı tazelenir.
                LaunchedEffect(Unit) { bildirimSayaci.yenile() }
                AkisEkrani(
                    viewModel { AkisViewModel(api) },
                    ustCubuk = {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(Res.string.akis_baslik), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { gezinti.navigate(BildirimlerRota) }) { Text(stringResource(Res.string.akis_bildirimler, okunmamis)) }
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
            composable<ListeRota> { listeGirdisi ->
                val listeId = listeGirdisi.toRoute<ListeRota>().listeId
                val listeVm = viewModel(key = "liste-$listeId") { ListeDetayViewModel(api, listeId) }
                LaunchedEffect(guncellenenListe) {
                    guncellenenListe?.takeIf { it.id == listeId }?.let {
                        listeVm.guncellendi(it)
                        guncellenenListe = null
                    }
                }
                ListeDetayEkrani(
                    listeVm,
                    oyunaGit = { gezinti.navigate(OyunRota(it)) },
                    duzenle = {
                        duzenlenenListe = it
                        gezinti.navigate(ListeDuzenleRota)
                    },
                    geri = { gezinti.popBackStack() },
                )
            }
            composable<ListeDuzenleRota> {
                val mevcut = duzenlenenListe
                ListeDuzenleEkrani(
                    viewModel(key = "liste-duzenle-${mevcut?.id ?: "yeni"}") { ListeDuzenleViewModel(api, mevcut) },
                    kaydedildi = { liste ->
                        guncellenenListe = liste
                        gezinti.popBackStack()
                        if (mevcut == null) gezinti.navigate(ListeRota(liste.id))
                    },
                    geri = { gezinti.popBackStack() },
                )
            }
            composable<GunlukDetayRota> { detayGirdisi ->
                val kayitId = detayGirdisi.toRoute<GunlukDetayRota>().kayitId
                GunlukDetayGorunumu(
                    viewModel(key = "gunluk-detay-$kayitId") { GunlukDetayViewModel(api, kayitId, aktifFaz = aktifFaz) },
                    api = api,
                    oyunaGit = { gezinti.navigate(OyunRota(it)) },
                    kullaniciyaGit = { gezinti.navigate(KullaniciRota(it)) },
                    geri = { gezinti.popBackStack() },
                )
            }
            composable<KesifRota> {
                KesifEkrani(
                    viewModel { KesifViewModel(api) },
                    oyunaGit = { gezinti.navigate(OyunRota(it)) },
                    turAc = { gezinti.navigate(OyunListesiRota(tur = it)) },
                    platformAc = { gezinti.navigate(OyunListesiRota(platform = it)) },
                    ekBolumler = {
                        if (aktifFaz.kapsar(Faz.FAZ_3)) {
                            OnerilerBolumu(viewModel { OnerilerViewModel(api) }) { gezinti.navigate(OyunRota(it)) }
                        }
                        if (aktifFaz.kapsar(Faz.FAZ_2)) {
                            PuanDegisimiBolumu(viewModel { PuanDegisimiViewModel(api) }) { gezinti.navigate(OyunRota(it)) }
                            KuratorListeleriBolumu(viewModel { KuratorListeleriViewModel(api) }) { gezinti.navigate(ListeRota(it)) }
                        }
                    },
                )
            }
            composable<OyunListesiRota> { listeGirdisi ->
                val rota = listeGirdisi.toRoute<OyunListesiRota>()
                OyunListesiGorunumu(
                    viewModel(key = "oyun-listesi-${rota.tur}-${rota.platform}") { OyunListesiViewModel(api, rota.tur, rota.platform) },
                    baslik = rota.tur?.let { turAdi(it) } ?: rota.platform.orEmpty(),
                    oyunaGit = { gezinti.navigate(OyunRota(it)) },
                    geri = { gezinti.popBackStack() },
                )
            }
            composable<DavetlerRota> {
                AnaEkran(
                    profil = profil,
                    davetKodlari = viewModel { DavetKodlarimViewModel(api) },
                    geri = { gezinti.popBackStack() },
                    steamAyari = viewModel { SteamAyariViewModel(api) },
                    bildirimTercihleri = viewModel { BildirimTercihleriViewModel(api) },
                    engelListesi = { gezinti.navigate(EngelListesiRota(it)) },
                    cikisYap = cikisYap,
                    hesapSilme = { viewModel { HesapSilmeViewModel(api, profil.kullaniciAdi, hesapSilindi) } },
                    gizlilik = if (aktifFaz.kapsar(Faz.FAZ_2)) ({ viewModel { GizlilikViewModel(api) } }) else null,
                    kurator = if (aktifFaz.kapsar(Faz.FAZ_2)) ({ viewModel { KuratorViewModel(api) } }) else null,
                    itiraz = if (aktifFaz.kapsar(Faz.FAZ_2)) ({ viewModel { ItirazViewModel(api) } }) else null,
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
                    listeyeGit = { gezinti.navigate(ListeRota(it)) },
                    yeniListe = {
                        duzenlenenListe = null
                        gezinti.navigate(ListeDuzenleRota)
                    },
                    yillikOzeteGit = { gezinti.navigate(YillikOzetRota(null, it)) },
                )
            }
            composable<YillikOzetRota> { girdi ->
                val rota = girdi.toRoute<YillikOzetRota>()
                YillikOzetGorunumu(
                    viewModel(key = "yillik-${rota.kullaniciAdi}-${rota.yil}") { YillikOzetViewModel(api, rota.kullaniciAdi, rota.yil) },
                    geri = { gezinti.popBackStack() },
                    oyunaGit = { gezinti.navigate(OyunRota(it)) },
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
                    listeyeGit = { gezinti.navigate(ListeRota(it)) },
                    yillikOzeteGit = { gezinti.navigate(YillikOzetRota(ad.takeIf { ad != profil.kullaniciAdi }, it)) },
                    sikayetPenceresi = { hedef, kapat ->
                        SikayetPenceresi(viewModel(key = "sikayet-$hedef") { SikayetViewModel(api, SikayetHedefi(hedef)) }, kapat)
                    },
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
            composable<BildirimlerRota> {
                BildirimlerGorunumu(
                    viewModel { BildirimlerViewModel(api) },
                    kullaniciyaGit = { gezinti.navigate(KullaniciRota(it)) },
                    gunlukKaydinaGit = { gezinti.navigate(GunlukDetayRota(it)) },
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
                    ekBolumler = {
                        if (aktifFaz.kapsar(Faz.FAZ_2)) {
                            PuanGecmisiBolumu(viewModel(key = "puan-gecmisi-${rota.oyunId}") { PuanGecmisiViewModel(api, rota.oyunId) })
                            OyunListeleriBolumu(viewModel(key = "oyun-listeleri-${rota.oyunId}") { OyunListeleriViewModel(api, rota.oyunId) }) {
                                gezinti.navigate(ListeRota(it))
                            }
                        }
                    },
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
