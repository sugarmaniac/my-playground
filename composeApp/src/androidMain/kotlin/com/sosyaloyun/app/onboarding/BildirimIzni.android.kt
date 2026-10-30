package com.sosyaloyun.app.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred

/** Android 13 öncesinde bildirim izni gerekmez. */
@Composable
actual fun bildirimIzniIsteyici(): suspend () -> Boolean {
    val context = LocalContext.current
    val bekleyen = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
    val izinIste = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { verildi ->
        bekleyen[0]?.complete(verildi)
    }
    return remember(context) {
        suspend {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                true
            } else {
                CompletableDeferred<Boolean>().also {
                    bekleyen[0] = it
                    izinIste.launch(Manifest.permission.POST_NOTIFICATIONS)
                }.await()
            }
        }
    }
}
