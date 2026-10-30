package com.sosyaloyun.app

import androidx.compose.ui.window.ComposeUIViewController
import com.sosyaloyun.app.oturum.IosGoogleGirisi
import com.sosyaloyun.app.oturum.iosGoogleGirisi
import platform.UIKit.UIViewController

/** [googleGirisi]: Swift tarafındaki GoogleSignIn akışı (#32). */
fun MainViewController(googleGirisi: IosGoogleGirisi): UIViewController {
    iosGoogleGirisi = googleGirisi
    return ComposeUIViewController { App() }
}
