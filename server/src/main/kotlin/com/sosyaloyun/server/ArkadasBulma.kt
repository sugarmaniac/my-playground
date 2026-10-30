package com.sosyaloyun.server

import com.sosyaloyun.shared.BulunanKisi
import com.sosyaloyun.shared.BulunanKisiler
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

/** Onboarding'in 6. adımı: rehberdeki (yalnızca hash'lerle) ve Steam arkadaşlarındaki uygulama kullanıcıları. */
class ArkadasBulma(private val veritabani: Database, private val steamKaynagi: SteamKaynagi?) {
    /** [hash] null ise kayıtlı numara silinir. */
    suspend fun telefonHashiniKaydet(kullaniciId: UUID, hash: String?) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) { KullaniciTablosu.update({ KullaniciTablosu.id eq kullaniciId }) { it[telefonHash] = hash } }
        }
    }

    suspend fun rehberden(izleyen: UUID, hashler: List<String>): BulunanKisiler = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            val idler = if (hashler.isEmpty()) emptyList() else KullaniciTablosu.select(KullaniciTablosu.id)
                .where { KullaniciTablosu.telefonHash inList hashler.distinct() }
                .map { it[KullaniciTablosu.id] }
            bulunanlar(izleyen, idler)
        }
    }

    sealed interface SteamSonucu {
        data class Bulunanlar(val kisiler: BulunanKisiler) : SteamSonucu
        data object BagliDegil : SteamSonucu
        data object Gizli : SteamSonucu
        data object Kullanilamiyor : SteamSonucu
    }

    suspend fun steamden(izleyen: UUID): SteamSonucu {
        val kaynak = steamKaynagi ?: return SteamSonucu.Kullanilamiyor
        val steamId = withContext(Dispatchers.IO) {
            transaction(veritabani) {
                SteamBaglantisiTablosu.selectAll().where { SteamBaglantisiTablosu.kullaniciId eq izleyen }
                    .singleOrNull()?.get(SteamBaglantisiTablosu.steamId)
            }
        } ?: return SteamSonucu.BagliDegil
        val arkadaslar = try {
            kaynak.arkadaslar(steamId) ?: return SteamSonucu.Gizli
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return SteamSonucu.Kullanilamiyor
        }
        return SteamSonucu.Bulunanlar(
            withContext(Dispatchers.IO) {
                transaction(veritabani) {
                    val idler = if (arkadaslar.isEmpty()) emptyList() else SteamBaglantisiTablosu.select(SteamBaglantisiTablosu.kullaniciId)
                        .where { SteamBaglantisiTablosu.steamId inList arkadaslar.distinct() }
                        .map { it[SteamBaglantisiTablosu.kullaniciId] }
                    bulunanlar(izleyen, idler)
                }
            },
        )
    }

    /** Kullanıcı adı seçmiş kişiler, ada göre; izleyen ve aralarında engel olanlar hariç. */
    private fun JdbcTransaction.bulunanlar(izleyen: UUID, idler: List<UUID>): BulunanKisiler {
        val adaylar = (idler.toSet() - izleyen - engellileri(izleyen).toSet()).toList()
        if (adaylar.isEmpty()) return BulunanKisiler(emptyList())
        val takipEdilenler = TakipTablosu.select(TakipTablosu.takipEdilen).where { TakipTablosu.takipEden eq izleyen }
            .map { it[TakipTablosu.takipEdilen] }.toSet()
        return BulunanKisiler(
            KullaniciTablosu.selectAll()
                .where { KullaniciTablosu.id inList adaylar }
                .orderBy(KullaniciTablosu.kullaniciAdi to SortOrder.ASC)
                .mapNotNull { satir -> satir.kullaniciOzeti()?.let { BulunanKisi(it, takipEdiyorum = satir[KullaniciTablosu.id] in takipEdilenler) } },
        )
    }
}
