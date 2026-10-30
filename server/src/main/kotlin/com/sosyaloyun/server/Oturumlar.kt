package com.sosyaloyun.server

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** Oturum token'ları: cihazda saklanır, sunucuda yalnızca özetleri tutulur. Çıkış yapılana kadar geçerlidir. */
class Oturumlar(private val veritabani: Database) {
    private val rastgele = SecureRandom()

    suspend fun ac(kullaniciId: UUID): String = withContext(Dispatchers.IO) {
        val token = ByteArray(32).also(rastgele::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        transaction(veritabani) {
            OturumTablosu.insert {
                it[tokenOzeti] = ozet(token)
                it[OturumTablosu.kullaniciId] = kullaniciId
            }
        }
        token
    }

    /** Token geçerli bir oturumunsa sahibini döner. */
    suspend fun sahibi(token: String): UUID? = withContext(Dispatchers.IO) {
        transaction(veritabani) {
            OturumTablosu.selectAll()
                .where { OturumTablosu.tokenOzeti eq ozet(token) }
                .singleOrNull()
                ?.get(OturumTablosu.kullaniciId)
        }
    }

    suspend fun kapat(token: String) {
        withContext(Dispatchers.IO) {
            transaction(veritabani) {
                OturumTablosu.deleteWhere { tokenOzeti eq ozet(token) }
            }
        }
    }

    private fun ozet(token: String): String =
        MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
}

private object OturumTablosu : Table("oturum") {
    val tokenOzeti = text("token_ozeti")
    val kullaniciId = javaUUID("kullanici_id")
    override val primaryKey = PrimaryKey(tokenOzeti)
}
