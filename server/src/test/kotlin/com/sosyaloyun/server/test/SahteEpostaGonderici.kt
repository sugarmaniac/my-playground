package com.sosyaloyun.server.test

import com.sosyaloyun.server.EpostaGonderici

/** Gönderilen e-postaları kaydeder: (konu, metin). */
class SahteEpostaGonderici : EpostaGonderici {
    val gonderilenler = mutableListOf<Pair<String, String>>()

    override suspend fun gonder(konu: String, metin: String) {
        gonderilenler += konu to metin
    }
}
