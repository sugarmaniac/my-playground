package com.sosyaloyun.app.hesap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun paylasici(): (metin: String) -> Unit = remember {
    { metin ->
        val sayfa = UIActivityViewController(activityItems = listOf(metin), applicationActivities = null)
        UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(sayfa, animated = true, completion = null)
    }
}
