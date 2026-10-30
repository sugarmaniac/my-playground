package com.sosyaloyun.server

/** Listelerin opak imleci: atlanan öğe sayısı. Sayfa, bir fazlası istenerek "devamı var mı" anlaşılır. */
internal object Imlec {
    fun baslangic(imlec: String?): Int = imlec?.toIntOrNull()?.coerceAtLeast(0) ?: 0

    /** [gelen] en fazla `sayfa + 1` öğe; fazlası varsa sonraki imleç. */
    fun sonraki(gelen: Int, baslangic: Int, sayfa: Int): String? = if (gelen > sayfa) (baslangic + sayfa).toString() else null
}
