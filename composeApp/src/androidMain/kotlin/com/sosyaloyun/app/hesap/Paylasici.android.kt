package com.sosyaloyun.app.hesap

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun paylasici(): (metin: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { metin ->
            val gonder = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, metin)
            }
            context.startActivity(Intent.createChooser(gonder, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
