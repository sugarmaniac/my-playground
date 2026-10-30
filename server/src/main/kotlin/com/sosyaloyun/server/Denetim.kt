package com.sosyaloyun.server

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.toJavaInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

data class DenetimKaydi(val zaman: OffsetDateTime, val yapan: String, val eylem: String, val ayrinti: String)

/** Yönetim panelindeki her kararın kaydı (#53): kim, ne zaman, ne yaptı. Faz kapalıyken de tutulur. */
class Denetim(private val veritabani: Database, private val saat: Clock) {
    suspend fun yaz(yapan: String, eylem: String, ayrinti: String) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                DenetimKaydiTablosu.insert {
                    it[id] = UUID.randomUUID()
                    it[zaman] = saat.now().toJavaInstant().atOffset(ZoneOffset.UTC)
                    it[DenetimKaydiTablosu.yapan] = yapan
                    it[DenetimKaydiTablosu.eylem] = eylem
                    it[DenetimKaydiTablosu.ayrinti] = ayrinti
                }
            }
        }
    }

    suspend fun son(adet: Int = 50): List<DenetimKaydi> = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            DenetimKaydiTablosu.selectAll().orderBy(DenetimKaydiTablosu.zaman to SortOrder.DESC).limit(adet)
                .map { DenetimKaydi(it[DenetimKaydiTablosu.zaman], it[DenetimKaydiTablosu.yapan], it[DenetimKaydiTablosu.eylem], it[DenetimKaydiTablosu.ayrinti]) }
        }
    }
}

internal object DenetimKaydiTablosu : Table("denetim_kaydi") {
    val id = javaUUID("id")
    val zaman = timestampWithTimeZone("zaman")
    val yapan = text("yapan")
    val eylem = text("eylem")
    val ayrinti = text("ayrinti")
    override val primaryKey = PrimaryKey(id)
}
