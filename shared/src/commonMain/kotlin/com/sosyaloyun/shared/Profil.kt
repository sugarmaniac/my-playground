package com.sosyaloyun.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Bir kullanıcının başkalarına görünen kimliği. */
@Serializable
data class KullaniciOzeti(
    val kullaniciId: String,
    val kullaniciAdi: String,
    val gorunenAd: String,
    val avatar: Avatar,
)

/** MVP'de avatar hazır bir setten seçilir; fotoğraf yükleme sonraki fazda. */
@Serializable
enum class Avatar {
    @SerialName("kumanda") KUMANDA,
    @SerialName("uzayli") UZAYLI,
    @SerialName("ejderha") EJDERHA,
    @SerialName("roket") ROKET,
    @SerialName("tilki") TILKI,
    @SerialName("kedi") KEDI,
    @SerialName("panda") PANDA,
    @SerialName("buyucu") BUYUCU,
    @SerialName("robot") ROBOT,
    @SerialName("kupa") KUPA,
    @SerialName("kilic") KILIC,
    @SerialName("hayalet") HAYALET,
}

/** `PUT /profil`: onboarding'in kullanıcı adı adımı; sonradan profili düzenlemek için de kullanılır. */
@Serializable
data class ProfilIstegi(
    val kullaniciAdi: String,
    val gorunenAd: String,
    val avatar: Avatar,
    val biyografi: String? = null,
)

@Serializable
enum class KullaniciAdiDurumu {
    @SerialName("musait") MUSAIT,
    @SerialName("alinmis") ALINMIS,
    @SerialName("gecersiz") GECERSIZ,
}

/** `GET /kullanici-adi/musaitlik?ad=...`: kullanıcı yazarken müsaitliği gösterir. */
@Serializable
data class KullaniciAdiMusaitlikYaniti(
    val kullaniciAdi: String,
    val durum: KullaniciAdiDurumu,
)

/** Kullanıcı adı ve görünen ad kuralları; istemci ve server aynı kuralı uygular. */
object ProfilKurallari {
    const val KULLANICI_ADI_EN_AZ = 3
    const val KULLANICI_ADI_EN_FAZLA = 20
    const val GORUNEN_AD_EN_FAZLA = 40
    const val BIYOGRAFI_EN_FAZLA = 300
    const val VITRIN_EN_FAZLA = 4

    private val izinliKarakterler = Regex("^[a-z0-9_.]+$")

    /** Kullanıcı adları küçük harfle saklanır ve karşılaştırılır; baştaki `@` ve boşluklar atılır. */
    fun kullaniciAdiniDuzenle(ad: String): String = ad.trim().removePrefix("@").lowercase()

    /** Yalnızca İngilizce küçük harf, rakam, `_` ve `.`; nokta başta, sonda veya art arda olamaz. */
    fun kullaniciAdiGecerliMi(duzenlenmisAd: String): Boolean =
        duzenlenmisAd.length in KULLANICI_ADI_EN_AZ..KULLANICI_ADI_EN_FAZLA &&
            izinliKarakterler.matches(duzenlenmisAd) &&
            !duzenlenmisAd.startsWith('.') &&
            !duzenlenmisAd.endsWith('.') &&
            ".." !in duzenlenmisAd

    fun gorunenAdGecerliMi(ad: String): Boolean = ad.trim().length in 1..GORUNEN_AD_EN_FAZLA
}

/** `GET /profilim`: profil başlığı. */
@Serializable
data class ProfilSayfasi(
    val kullanici: KullaniciOzeti,
    val biyografi: String?,
    /** Bu takvim yılında (Türkiye saati) bitirilen oyun sayısı. */
    val buYilBitirilen: Int,
    /** Favori dört oyun, seçilen sırayla. */
    val vitrin: List<OyunOzeti>,
    val takipciSayisi: Int = 0,
    val takipEdilenSayisi: Int = 0,
    /** İsteyen bu kullanıcıyı takip ediyor mu; kendi profilinde null. */
    val takipEdiyorum: Boolean? = null,
    val arkadasSayisi: Int = 0,
    /** İsteyenle bu kullanıcı arasındaki arkadaşlık; kendi profilinde null. */
    val arkadaslik: ArkadaslikDurumu? = null,
    /** İsteyen bu kullanıcıyı sessize aldı mı; kendi profilinde null. */
    val sessizeAldim: Boolean? = null,
    /** Faz 2 (#48): profil gizli mi (kendi profilinde ayarın değeri). */
    val gizli: Boolean = false,
    /** Gizli profilde takip isteği bekliyor mu; kendi profilinde null. */
    val takipIstegiGonderdim: Boolean? = null,
    /** İsteyen bu gizli profilin içeriğini göremiyor (takipçi değil): sekmeler ve vitrin boş gelir. */
    val icerikGizli: Boolean = false,
    /** Faz 2 (#50): onaylanmış küratör; profilde rozetle gösterilir. */
    val kurator: Boolean = false,
)

/** `PUT /gizlilik` (Faz 2, #48). Gizliden açığa geçince bekleyen takip istekleri kabul edilir. */
@Serializable
data class GizlilikIstegi(val gizli: Boolean)

@Serializable
enum class ArkadaslikDurumu {
    @SerialName("yok") YOK,
    @SerialName("istek_gonderildi") ISTEK_GONDERILDI,
    @SerialName("istek_alindi") ISTEK_ALINDI,
    @SerialName("arkadas") ARKADAS,
}

/** Arkadaşın profilinde: ikinizin de oynadığı oyunlar ve onun bitirip senin istek listende olanlar. */
@Serializable
data class OrtakOyunlar(
    val ortakOyunlar: List<OyunOzeti>,
    val istekListesiEslesmeleri: List<OyunOzeti>,
)

/** Takipçi, takip edilen ve kullanıcı araması listeleri. */
@Serializable
data class KullaniciListesi(val kullanicilar: List<KullaniciOzeti>, val sonrakiImlec: String?)

/** `PUT /profilim/vitrin`: en fazla dört oyun, sırasıyla. */
@Serializable
data class VitrinIstegi(val oyunIdleri: List<String>)

/** Profilin Günlük ve İncelemeler sekmelerinde bir günlük kaydı ve oyunu. */
@Serializable
data class GunlukOgesi(val kayit: GunlukKaydi, val oyun: OyunOzeti)

@Serializable
data class GunlukSayfasi(val ogeler: List<GunlukOgesi>, val sonrakiImlec: String?)

/** Profilin Oyunlar ve İstek listesi sekmelerinde bir kütüphane girişi ve oyunu. */
@Serializable
data class KutuphaneOgesi(val oyun: OyunOzeti, val giris: KutuphaneGirisi)

@Serializable
data class KutuphaneSayfasi(val ogeler: List<KutuphaneOgesi>, val sonrakiImlec: String?)
