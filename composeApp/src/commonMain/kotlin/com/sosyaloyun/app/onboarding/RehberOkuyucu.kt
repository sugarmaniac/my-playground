package com.sosyaloyun.app.onboarding

import androidx.compose.runtime.Composable

/** Cihaz rehberindeki telefon numaraları; izin verilmezse null. Numaralar cihazdan çıkmadan hash'lenir. */
fun interface RehberOkuyucu {
    suspend fun numaralar(): List<String>?
}

@Composable
expect fun rehberOkuyucu(): RehberOkuyucu
