package com.sosyaloyun.server

import java.util.concurrent.ConcurrentHashMap
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Herkese açık katalog yanıtları için süreli bellek önbelleği (#62). [sure] sıfırsa önbelleksizdir (testler).
 * Süre dolana kadar katalog güncellemesi görünmeyebilir; katalog verisi için kabul edilen gecikme bu.
 * Null sonuç saklanmaz. Aynı anahtar için eşzamanlı ilk istekler veritabanına birlikte gidebilir.
 */
class SureliOnbellek<K : Any, V : Any>(
    private val sure: Duration,
    private val enCok: Int = 2_000,
    private val zaman: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) {
    private class Kayit<V>(val deger: V, val bitis: ComparableTimeMark)

    private val kayitlar = ConcurrentHashMap<K, Kayit<V>>()

    suspend fun getir(anahtar: K, uret: suspend () -> V?): V? {
        if (!sure.isPositive()) return uret()
        kayitlar[anahtar]?.takeIf { it.bitis.hasNotPassedNow() }?.let { return it.deger }
        val deger = uret() ?: return null
        // Sınır aşılırsa hepsi atılır: anahtarlar oyun ve sayfa sayısıyla sınırlı, nadiren olur.
        if (kayitlar.size >= enCok) kayitlar.clear()
        kayitlar[anahtar] = Kayit(deger, zaman.markNow() + sure)
        return deger
    }
}
