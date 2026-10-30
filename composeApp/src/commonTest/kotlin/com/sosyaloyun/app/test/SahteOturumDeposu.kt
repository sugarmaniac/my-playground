package com.sosyaloyun.app.test

import com.sosyaloyun.app.oturum.OturumDeposu

class SahteOturumDeposu(var token: String? = null) : OturumDeposu {
    override fun oku(): String? = token

    override fun yaz(token: String?) {
        this.token = token
    }
}
