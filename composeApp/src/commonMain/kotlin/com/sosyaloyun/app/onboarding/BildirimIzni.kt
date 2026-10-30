package com.sosyaloyun.app.onboarding

import androidx.compose.runtime.Composable

/** Sistemin bildirim izni penceresini açar; izin verildiyse true. */
@Composable
expect fun bildirimIzniIsteyici(): suspend () -> Boolean
