package com.sosyaloyun.app.kesif

import androidx.compose.runtime.Composable
import com.sosyaloyun.app.resources.tur_macera
import com.sosyaloyun.app.resources.tur_rol_yapma
import com.sosyaloyun.app.resources.tur_nisanci
import com.sosyaloyun.app.resources.tur_strateji
import com.sosyaloyun.app.resources.tur_gercek_zamanli_strateji
import com.sosyaloyun.app.resources.tur_sira_tabanli_strateji
import com.sosyaloyun.app.resources.tur_taktik
import com.sosyaloyun.app.resources.tur_simulasyon
import com.sosyaloyun.app.resources.tur_spor
import com.sosyaloyun.app.resources.tur_yaris
import com.sosyaloyun.app.resources.tur_dovus
import com.sosyaloyun.app.resources.tur_platform
import com.sosyaloyun.app.resources.tur_bulmaca
import com.sosyaloyun.app.resources.tur_kes_bicle
import com.sosyaloyun.app.resources.tur_arcade
import com.sosyaloyun.app.resources.tur_bagimsiz
import com.sosyaloyun.app.resources.tur_gorsel_roman
import com.sosyaloyun.app.resources.tur_kart_ve_kutu
import com.sosyaloyun.app.resources.tur_moba
import com.sosyaloyun.app.resources.tur_muzik
import com.sosyaloyun.app.resources.tur_tikla_ve_kesfet
import com.sosyaloyun.app.resources.tur_bilgi_yarismasi
import com.sosyaloyun.app.resources.tur_pinball
import com.sosyaloyun.app.resources.Res
import com.sosyaloyun.shared.Tur
import org.jetbrains.compose.resources.stringResource

/** Türün arayüzdeki adı. */
@Composable
fun turAdi(tur: Tur): String = stringResource(
    when (tur) {
        Tur.MACERA -> Res.string.tur_macera
        Tur.ROL_YAPMA -> Res.string.tur_rol_yapma
        Tur.NISANCI -> Res.string.tur_nisanci
        Tur.STRATEJI -> Res.string.tur_strateji
        Tur.GERCEK_ZAMANLI_STRATEJI -> Res.string.tur_gercek_zamanli_strateji
        Tur.SIRA_TABANLI_STRATEJI -> Res.string.tur_sira_tabanli_strateji
        Tur.TAKTIK -> Res.string.tur_taktik
        Tur.SIMULASYON -> Res.string.tur_simulasyon
        Tur.SPOR -> Res.string.tur_spor
        Tur.YARIS -> Res.string.tur_yaris
        Tur.DOVUS -> Res.string.tur_dovus
        Tur.PLATFORM -> Res.string.tur_platform
        Tur.BULMACA -> Res.string.tur_bulmaca
        Tur.KES_BICLE -> Res.string.tur_kes_bicle
        Tur.ARCADE -> Res.string.tur_arcade
        Tur.BAGIMSIZ -> Res.string.tur_bagimsiz
        Tur.GORSEL_ROMAN -> Res.string.tur_gorsel_roman
        Tur.KART_VE_KUTU -> Res.string.tur_kart_ve_kutu
        Tur.MOBA -> Res.string.tur_moba
        Tur.MUZIK -> Res.string.tur_muzik
        Tur.TIKLA_VE_KESFET -> Res.string.tur_tikla_ve_kesfet
        Tur.BILGI_YARISMASI -> Res.string.tur_bilgi_yarismasi
        Tur.PINBALL -> Res.string.tur_pinball
    },
)

/** Katalogdaki IGDB tür adının çevirisi; tanınmayan tür olduğu gibi gösterilir. */
@Composable
fun igdbTurAdi(igdbAdi: String): String = Tur.igdbAdindan(igdbAdi)?.let { turAdi(it) } ?: igdbAdi
