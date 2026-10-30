package com.sosyaloyun.server

import java.text.Normalizer

/** Aramada Türkçe karakterli ve karaktersiz yazımların, aksanlı ve aksansız adların aynı sayılması için. */
object AramaMetni {
    private val birlesikIsaretler = Regex("\\p{M}+")
    private val harfVeRakamDisi = Regex("[^a-z0-9]+")

    fun duzenle(metin: String): String {
        val kucuk = metin.replace('ı', 'i').replace('I', 'i').replace('İ', 'i').lowercase()
        return Normalizer.normalize(kucuk, Normalizer.Form.NFD)
            .replace(birlesikIsaretler, "")
            .replace(harfVeRakamDisi, " ")
            .trim()
    }

    fun kelimeler(metin: String): List<String> = duzenle(metin).split(' ').filter { it.isNotEmpty() }
}
