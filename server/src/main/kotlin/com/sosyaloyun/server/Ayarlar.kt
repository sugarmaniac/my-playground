package com.sosyaloyun.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert

/** Veritabanında tutulan, betadan sonra veriyle ayarlanacak bir eşik veya sınır. */
sealed class Ayar<T>(
    val anahtar: String,
    val varsayilan: T,
    internal val metne: (T) -> String,
    internal val metinden: (String) -> T,
) {
    data object BakimModu : Ayar<Boolean>("bakim_modu", false, Boolean::toString, String::toBooleanStrict)

    /** Açık faz (1–3); yönetim panelinden değişir (#46). */
    data object AktifFaz : Ayar<Int>("aktif_faz", 1, Int::toString, String::toInt)

    /** Betaya katılan her kullanıcının dağıtabileceği davet kodu sayısı. */
    data object KisiBasiDavetKoduSayisi : Ayar<Int>("kisi_basi_davet_kodu_sayisi", 5, Int::toString, String::toInt)

    /** Bir kullanıcıya bir günde (kendi saatiyle) gönderilecek en fazla push; fazlası yalnızca uygulama içinde kalır. */
    data object GunlukPushSiniri : Ayar<Int>("gunluk_push_siniri", 4, Int::toString, String::toInt)

    /** Sessiz saatlerin başlangıcı ve bitişi (kullanıcının yerel saati, 0–23); bu aralıkta push gitmez. */
    data object SessizSaatBaslangici : Ayar<Int>("sessiz_saat_baslangici", 23, Int::toString, String::toInt)
    data object SessizSaatBitisi : Ayar<Int>("sessiz_saat_bitisi", 9, Int::toString, String::toInt)

    /** Moderatörün "askıya al" kararının süresi (gün). */
    data object AskiSuresiGun : Ayar<Int>("aski_suresi_gun", 7, Int::toString, String::toInt)

    /** Küratör başvurusu şartları (#50): en az inceleme, takipçi ve hesap yaşı (davetle katılıştan beri gün). */
    data object KuratorEnAzInceleme : Ayar<Int>("kurator_en_az_inceleme", 20, Int::toString, String::toInt)
    data object KuratorEnAzTakipci : Ayar<Int>("kurator_en_az_takipci", 50, Int::toString, String::toInt)
    data object KuratorEnAzHesapGunu : Ayar<Int>("kurator_en_az_hesap_gunu", 30, Int::toString, String::toInt)

    /** Topluluk puanının gösterilmesi için gereken en az puan veren kullanıcı sayısı. */
    data object ToplulukPuaniEsigi : Ayar<Int>("topluluk_puani_esigi", 5, Int::toString, String::toInt)
}

class Ayarlar(private val veritabani: Database) {
    suspend fun <T> oku(ayar: Ayar<T>): T = withContext(Dispatchers.IO) {
        val deger = transaction(veritabani) {
            AyarTablosu.selectAll()
                .where { AyarTablosu.anahtar eq ayar.anahtar }
                .singleOrNull()
                ?.get(AyarTablosu.deger)
        }
        deger?.let(ayar.metinden) ?: ayar.varsayilan
    }

    suspend fun <T> yaz(ayar: Ayar<T>, deger: T) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                AyarTablosu.upsert {
                    it[anahtar] = ayar.anahtar
                    it[AyarTablosu.deger] = ayar.metne(deger)
                }
            }
        }
    }
}

private object AyarTablosu : Table("ayar") {
    val anahtar = text("anahtar")
    val deger = text("deger")
    override val primaryKey = PrimaryKey(anahtar)
}
