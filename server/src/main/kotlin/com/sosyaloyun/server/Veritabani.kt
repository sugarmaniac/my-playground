package com.sosyaloyun.server

import javax.sql.DataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database

/** Şema göçlerini (`db/migration`) uygular ve Exposed bağlantısını döner. */
fun veritabaniniHazirla(kaynak: DataSource): Database {
    Flyway.configure()
        .dataSource(kaynak)
        .load()
        .migrate()
    return Database.connect(kaynak)
}
