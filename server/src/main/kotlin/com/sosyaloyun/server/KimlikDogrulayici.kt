package com.sosyaloyun.server

import com.sosyaloyun.shared.KimlikSaglayici

/** Apple/Google'ın verdiği kimlik token'ını doğrulayan adaptör (ADR 0006). */
interface KimlikDogrulayici {
    /** Token geçerliyse sağlayıcıdaki kalıcı kullanıcı kimliğini (`sub`), değilse null döner. */
    suspend fun dogrula(saglayici: KimlikSaglayici, kimlikTokeni: String): DogrulanmisKimlik?
}

data class DogrulanmisKimlik(val haricKimlik: String)
