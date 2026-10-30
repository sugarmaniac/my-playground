package com.sosyaloyun.server.test

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** Testlerin ileri sarabildiği saat; sessiz saatler, günlük sınırlar ve haftalık metrikler bununla test edilir. */
class SahteSaat(var simdi: Instant = Instant.parse("2026-01-05T09:00:00Z")) : Clock {
    override fun now(): Instant = simdi

    fun ilerlet(sure: Duration) {
        simdi += sure
    }
}
