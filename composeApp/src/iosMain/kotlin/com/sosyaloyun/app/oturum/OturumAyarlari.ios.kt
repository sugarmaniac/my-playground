package com.sosyaloyun.app.oturum

import com.russhwolf.settings.ExperimentalSettingsImplementation
import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.Settings

@OptIn(ExperimentalSettingsImplementation::class)
actual fun oturumAyarlari(): Settings = KeychainSettings(service = "com.sosyaloyun.app.oturum")
