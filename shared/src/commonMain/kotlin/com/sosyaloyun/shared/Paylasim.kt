package com.sosyaloyun.shared

import kotlinx.serialization.Serializable

/**
 * `GET /paylasim/profil/{kullaniciAdi}`: web sitesindeki profil paylaşım sayfası. Oturum gerektirmez; yalnızca
 * Herkes görünürlüğündeki günlük kayıtları döner. İzleyen olmadığından profildeki ilişki alanları boştur.
 */
@Serializable
data class PaylasilanProfil(val profil: ProfilSayfasi, val sonKayitlar: List<GunlukOgesi>)

/** Uygulamadaki paylaş butonunun ürettiği web sitesi linkleri (ADR 0004); sayfalar `web/` altında. */
object PaylasimLinkleri {
    fun gunlukKaydi(webAdresi: String, kayitId: String) = "${webAdresi.trimEnd('/')}/kayit.html?id=$kayitId"

    fun profil(webAdresi: String, kullaniciAdi: String) = "${webAdresi.trimEnd('/')}/profil.html?ad=$kullaniciAdi"

    /** Herkes görünürlüğündeki son kayıtlardan profil sayfasında gösterilenler. */
    const val PROFILDEKI_KAYIT_SAYISI = 20
}
