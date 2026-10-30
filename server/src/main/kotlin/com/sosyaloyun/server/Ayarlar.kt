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

    /** Betaya katılan her kullanıcının dağıtabileceği davet kodu sayısı. */
    data object KisiBasiDavetKoduSayisi : Ayar<Int>("kisi_basi_davet_kodu_sayisi", 5, Int::toString, String::toInt)
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
