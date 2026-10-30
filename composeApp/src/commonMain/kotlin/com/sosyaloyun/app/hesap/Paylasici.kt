package com.sosyaloyun.app.hesap

import androidx.compose.runtime.Composable

/** Sistemin paylaşma sayfasını açan işlev (Android paylaşım seçici, iOS paylaşım sayfası). */
@Composable
expect fun paylasici(): (metin: String) -> Unit
