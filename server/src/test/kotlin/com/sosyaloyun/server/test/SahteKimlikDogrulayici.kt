package com.sosyaloyun.server.test

import com.sosyaloyun.server.DogrulanmisKimlik
import com.sosyaloyun.server.KimlikDogrulayici
import com.sosyaloyun.shared.KimlikSaglayici

/** Yalnızca [tokenUret] ile üretilmiş token'ları kabul eden sahte Apple/Google doğrulayıcısı. */
class SahteKimlikDogrulayici : KimlikDogrulayici {
    fun tokenUret(saglayici: KimlikSaglayici, haricKimlik: String): String = "sahte:${saglayici.name}:$haricKimlik"

    override suspend fun dogrula(saglayici: KimlikSaglayici, kimlikTokeni: String): DogrulanmisKimlik? {
        val parcalar = kimlikTokeni.split(":", limit = 3)
        if (parcalar.size != 3 || parcalar[0] != "sahte" || parcalar[1] != saglayici.name) return null
        return DogrulanmisKimlik(haricKimlik = parcalar[2])
    }
}
