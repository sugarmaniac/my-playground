package com.sosyaloyun.app.onbellek

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

// Oturum anahtarlıkta; önbellek ayrı bir kullanıcı varsayılanları kümesinde.
@Composable
actual fun onbellekAyarlari(): Settings = remember { NSUserDefaultsSettings(NSUserDefaults(suiteName = "com.sosyaloyun.app.onbellek")) }
