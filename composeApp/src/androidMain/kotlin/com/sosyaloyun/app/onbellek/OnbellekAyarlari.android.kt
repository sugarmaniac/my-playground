package com.sosyaloyun.app.onbellek

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

// Oturumun varsayılan dosyasından ayrı: büyük yanıtlar oturum okunurken belleğe yüklenmesin.
@Composable
actual fun onbellekAyarlari(): Settings {
    val context = LocalContext.current.applicationContext
    return remember(context) { SharedPreferencesSettings(context.getSharedPreferences("onbellek", Context.MODE_PRIVATE)) }
}
