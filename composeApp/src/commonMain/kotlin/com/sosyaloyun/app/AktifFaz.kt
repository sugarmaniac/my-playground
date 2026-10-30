package com.sosyaloyun.app

import androidx.compose.runtime.staticCompositionLocalOf
import com.sosyaloyun.shared.Faz

/**
 * Açık faz (#46). Uygulama açılışta `GET /ben`'den öğrenir; ekranlar kapalı fazın girişlerini bununla gizler.
 * Backend kapalı fazın uç noktalarını zaten reddeder; bu yalnızca arayüzü düzenler.
 */
val LocalAktifFaz = staticCompositionLocalOf { Faz.FAZ_1 }
