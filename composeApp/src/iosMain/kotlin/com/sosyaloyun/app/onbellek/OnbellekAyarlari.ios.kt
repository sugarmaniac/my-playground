package com.sosyaloyun.app.onbellek

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

// Oturum anahtarlıkta; önbellek ayrı bir kullanıcı varsayılanları kümesinde.
actual fun onbellekAyarlari(): Settings = NSUserDefaultsSettings(NSUserDefaults(suiteName = "com.sosyaloyun.app.onbellek"))
