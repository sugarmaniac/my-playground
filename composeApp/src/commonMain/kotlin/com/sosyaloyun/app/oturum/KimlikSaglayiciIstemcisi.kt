package com.sosyaloyun.app.oturum

import androidx.compose.runtime.Composable
import com.sosyaloyun.shared.KimlikSaglayici

/** Cihazdaki Apple/Google giriş akışı; yalnızca o platformda kullanılabilen sağlayıcıları sunar. */
interface KimlikSaglayiciIstemcisi {
    val desteklenenler: List<KimlikSaglayici>

    suspend fun kimlikTokeniAl(saglayici: KimlikSaglayici): KimlikTokeniSonucu
}

@Composable
expect fun kimlikSaglayiciIstemcisi(): KimlikSaglayiciIstemcisi
