package com.sosyaloyun.app.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rehberOkuyucu(): RehberOkuyucu {
    val context = LocalContext.current
    val bekleyen = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
    val izinIste = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { verildi ->
        bekleyen[0]?.complete(verildi)
    }
    return remember(context) {
        RehberOkuyucu {
            val izinli = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED ||
                CompletableDeferred<Boolean>().also {
                    bekleyen[0] = it
                    izinIste.launch(Manifest.permission.READ_CONTACTS)
                }.await()
            if (izinli) numaralariOku(context) else null
        }
    }
}

private suspend fun numaralariOku(context: Context): List<String> = withContext(Dispatchers.IO) {
    val numaralar = mutableListOf<String>()
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        null,
        null,
        null,
    )?.use { imlec ->
        val sutun = imlec.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (imlec.moveToNext()) imlec.getString(sutun)?.let(numaralar::add)
    }
    numaralar
}
