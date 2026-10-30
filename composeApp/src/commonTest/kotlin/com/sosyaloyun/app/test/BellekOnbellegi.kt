package com.sosyaloyun.app.test

import com.sosyaloyun.app.onbellek.YerelOnbellek

/** Testlerde cihaz deposunun yerine. */
class BellekOnbellegi : YerelOnbellek {
    val kayitlar = mutableMapOf<String, String>()
    override fun oku(anahtar: String): String? = kayitlar[anahtar]
    override fun yaz(anahtar: String, deger: String) {
        kayitlar[anahtar] = deger
    }
    override fun temizle() = kayitlar.clear()
}
