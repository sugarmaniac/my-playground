package com.sosyaloyun.app.onboarding

import androidx.compose.runtime.Composable

@Composable
actual fun rehberOkuyucu(): RehberOkuyucu = RehberOkuyucu { null }

@Composable
actual fun bildirimIzniIsteyici(): suspend () -> Boolean = { false }
