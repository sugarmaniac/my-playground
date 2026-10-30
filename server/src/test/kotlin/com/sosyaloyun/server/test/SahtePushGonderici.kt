package com.sosyaloyun.server.test

import com.sosyaloyun.server.PushGonderici

/** Gönderilen push'ları kaydeder: (token, metin). */
class SahtePushGonderici : PushGonderici {
    val gonderilenler = mutableListOf<Pair<String, String>>()

    override suspend fun gonder(token: String, baslik: String, metin: String) {
        gonderilenler += token to metin
    }
}
