package com.sosyaloyun.app.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CompletableDeferred
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

@Composable
actual fun bildirimIzniIsteyici(): suspend () -> Boolean = remember {
    suspend {
        val sonuc = CompletableDeferred<Boolean>()
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound,
        ) { verildi, _ -> sonuc.complete(verildi) }
        sonuc.await()
    }
}
