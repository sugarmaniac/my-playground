package com.sosyaloyun.app.oturum

import androidx.compose.runtime.Composable
import com.russhwolf.settings.Settings
import com.sosyaloyun.shared.KimlikSaglayici

actual fun oturumAyarlari(): Settings = Settings()

@Composable
actual fun kimlikSaglayiciIstemcisi(): KimlikSaglayiciIstemcisi = object : KimlikSaglayiciIstemcisi {
    override val desteklenenler: List<KimlikSaglayici> = listOf(KimlikSaglayici.GOOGLE)
    override suspend fun kimlikTokeniAl(saglayici: KimlikSaglayici): KimlikTokeniSonucu = KimlikTokeniSonucu.Iptal
}
