package com.sosyaloyun.server.test

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import org.testcontainers.postgresql.PostgreSQLContainer

/** Bütün testlerin paylaştığı tek PostgreSQL konteyneri; testler arasında tablolar boşaltılır. */
object TestVeritabani {
    private val konteyner: PostgreSQLContainer by lazy {
        PostgreSQLContainer("postgres:16-alpine").apply { start() }
    }

    val kaynak: DataSource by lazy {
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = konteyner.jdbcUrl
                username = konteyner.username
                password = konteyner.password
                maximumPoolSize = 4
            },
        )
    }

    fun temizle() {
        kaynak.connection.use { baglanti ->
            val tablolar = baglanti.createStatement().use { ifade ->
                ifade.executeQuery(
                    "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'",
                ).use { sonuc ->
                    buildList { while (sonuc.next()) add(sonuc.getString(1)) }
                }
            }
            if (tablolar.isNotEmpty()) {
                baglanti.createStatement().use { it.execute("TRUNCATE ${tablolar.joinToString()} CASCADE") }
            }
        }
    }
}
