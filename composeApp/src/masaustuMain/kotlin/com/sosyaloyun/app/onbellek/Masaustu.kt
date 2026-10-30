package com.sosyaloyun.app.onbellek

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.Settings

@Composable
actual fun onbellekAyarlari(): Settings = remember { Settings() }
