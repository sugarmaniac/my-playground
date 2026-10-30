package com.sosyaloyun.app.onbellek

import androidx.compose.runtime.staticCompositionLocalOf
import com.russhwolf.settings.Settings
import com.sosyaloyun.shared.ApiJson
import com.sosyaloyun.shared.OyunOzeti
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Ekranların son yanıtları (#60): ekran açılır açılmaz son görülen içerik gösterilir, arkadan tazelenir.
 * Yalnız küçük yanıtlar saklanır; oturum kapanınca silinir.
 */
interface YerelOnbellek {
    fun oku(anahtar: String): String?
    fun yaz(anahtar: String, deger: String)
    fun temizle()
}

/** Önbelleksiz: testler ve önizlemeler için varsayılan. */
object BosOnbellek : YerelOnbellek {
    override fun oku(anahtar: String): String? = null
    override fun yaz(anahtar: String, deger: String) = Unit
    override fun temizle() = Unit
}

/** Cihazın ayar deposunda; anahtarlar oturum bilgileriyle karışmasın diye önekli. */
class AyarliOnbellek(private val ayarlar: Settings = onbellekAyarlari()) : YerelOnbellek {
    override fun oku(anahtar: String): String? = ayarlar.getStringOrNull(ONEK + anahtar)

    override fun yaz(anahtar: String, deger: String) {
        if (deger.length <= EN_BUYUK_DEGER) ayarlar.putString(ONEK + anahtar, deger)
    }

    override fun temizle() {
        ayarlar.keys.filter { it.startsWith(ONEK) }.forEach(ayarlar::remove)
    }

    private companion object {
        const val ONEK = "onbellek."
        const val EN_BUYUK_DEGER = 200_000
    }
}

expect fun onbellekAyarlari(): Settings

/** Okunamayan (ör. eski sürümün biçimindeki) kayıt yok sayılır. */
fun <T> YerelOnbellek.oku(anahtar: String, serilestirici: KSerializer<T>): T? =
    try {
        oku(anahtar)?.let { ApiJson.decodeFromString(serilestirici, it) }
    } catch (e: IllegalArgumentException) {
        null
    }

fun <T> YerelOnbellek.yaz(anahtar: String, serilestirici: KSerializer<T>, deger: T) = yaz(anahtar, ApiJson.encodeToString(serilestirici, deger))

inline fun <reified T> YerelOnbellek.okuNesne(anahtar: String): T? = oku(anahtar, serializer<T>())

inline fun <reified T> YerelOnbellek.yazNesne(anahtar: String, deger: T) = yaz(anahtar, serializer<T>(), deger)

val LocalYerelOnbellek = staticCompositionLocalOf<YerelOnbellek> { BosOnbellek }

/**
 * Listelerde görülen oyunların özetleri, yalnız bellekte: oyun detayı açılınca bant, kapak ve ad beklemeden çizilir.
 * Ana iş parçacığından kullanılır.
 */
object OyunOzetiBellegi {
    private const val EN_COK = 500
    private val ozetler = LinkedHashMap<String, OyunOzeti>()

    fun ekle(oyunlar: Iterable<OyunOzeti>) {
        oyunlar.forEach { ozetler.remove(it.id); ozetler[it.id] = it }
        while (ozetler.size > EN_COK) ozetler.remove(ozetler.keys.first())
    }

    fun bul(oyunId: String): OyunOzeti? = ozetler[oyunId]
}
