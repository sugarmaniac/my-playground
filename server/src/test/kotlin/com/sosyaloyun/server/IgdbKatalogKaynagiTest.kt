package com.sosyaloyun.server

import com.sosyaloyun.shared.Magaza
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

/** Gerçek IGDB adaptörü, sahte HTTP motoruyla: kimlik doğrulama, sorgular, tür eşlemesi ve istek sınırı. */
class IgdbKatalogKaynagiTest {
    private val istekler = mutableListOf<HttpRequestData>()
    private var tokenSayisi = 0
    private var oyunYaniti = "[]"
    private var siradakiOyunIstegiYetkisiz = false

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun MockRequestHandleScope.yanitla(istek: HttpRequestData): HttpResponseData {
        istekler += istek
        return when {
            istek.url.host == "id.twitch.tv" -> {
                tokenSayisi++
                respond("""{"access_token":"token-$tokenSayisi","expires_in":5000000,"token_type":"bearer"}""", headers = json)
            }
            siradakiOyunIstegiYetkisiz -> {
                siradakiOyunIstegiYetkisiz = false
                respond("", HttpStatusCode.Unauthorized)
            }
            istek.url.encodedPath == "/v4/popularity_primitives" ->
                respond("""[{"id":9,"game_id":100,"value":0.0042,"popularity_type":1}]""", headers = json)
            else -> respond(oyunYaniti, headers = json)
        }
    }

    private fun TestScope.kaynak() = IgdbKatalogKaynagi(
        istemciKimligi = "istemci",
        istemciSirri = "sir",
        http = HttpClient(MockEngine { yanitla(it) }),
        simdi = { currentTime },
    )

    private val HttpRequestData.govdeMetni: String get() = (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    @Test
    fun oyunSorgusuIstemciKimligiVeTokenlaGonderilir() = runTest {
        kaynak().oyunSayfasi(sonKaynakId = 41, adet = 500)

        val token = istekler.first()
        assertEquals("https://id.twitch.tv/oauth2/token", token.url.toString().substringBefore('?'))
        assertEquals("client_credentials", token.url.parameters["grant_type"])
        val sorgu = istekler.last()
        assertEquals("https://api.igdb.com/v4/games", sorgu.url.toString())
        assertEquals("istemci", sorgu.headers["Client-ID"])
        assertEquals("Bearer token-1", sorgu.headers[HttpHeaders.Authorization])
        assertTrue("where id > 41;" in sorgu.govdeMetni, sorgu.govdeMetni)
        assertTrue("sort id asc;" in sorgu.govdeMetni)
        assertTrue("limit 500;" in sorgu.govdeMetni)
    }

    @Test
    fun igdbAlanlariKatalogKaydinaEslenir() = runTest {
        oyunYaniti = """[
          {"id":100,"name":"Elden Ring","game_type":0,"first_release_date":1645747200,"cover":{"id":1,"image_id":"co4jni"},
           "platforms":[{"id":6,"name":"PC (Microsoft Windows)"},{"id":167,"name":"PlayStation 5"}],"summary":"Özet",
           "external_games":[{"id":1,"uid":"1245620","external_game_source":1},{"id":2,"uid":"abc","external_game_source":5}],
           "slug":"elden-ring","genres":[{"id":12,"name":"Role-playing (RPG)"},{"id":31,"name":"Adventure"}],
           "involved_companies":[
             {"id":1,"company":{"id":1,"name":"FromSoftware"},"developer":true,"publisher":false},
             {"id":2,"company":{"id":2,"name":"Bandai Namco Entertainment"},"developer":false,"publisher":true}],
           "websites":[{"id":1,"url":"https://en.bandainamcoent.eu/elden-ring","type":1},
             {"id":2,"url":"https://store.steampowered.com/app/1245620","type":13},
             {"id":3,"url":"https://twitter.com/eldenring","type":5},
             {"id":4,"url":"https://www.gog.com/game/elden_ring","category":17}]},
          {"id":101,"name":"Shadow of the Erdtree","game_type":2,"parent_game":100},
          {"id":102,"name":"Elden Ring Nightreign Edition","game_type":0,"version_parent":100},
          {"id":103,"name":"Eski Kategorili DLC","category":1,"parent_game":100},
          {"id":104,"name":"Port","game_type":11,"parent_game":100},
          {"id":105,"name":"Paket","game_type":3},
          {"id":106}
        ]"""

        val oyunlar = kaynak().oyunSayfasi(0, 500).associateBy { it.kaynakId }

        assertEquals(
            KaynakOyun(
                kaynakId = 100,
                ad = "Elden Ring",
                tur = KaynakTuru.ANA_OYUN,
                cikisTarihi = LocalDate.of(2022, 2, 25),
                kapakKimligi = "co4jni",
                platformlar = listOf("PC (Microsoft Windows)", "PlayStation 5"),
                steamUygulamaIdleri = listOf(1245620),
                ozet = "Özet",
                slug = "elden-ring",
                gelistiriciler = listOf("FromSoftware"),
                yayincilar = listOf("Bandai Namco Entertainment"),
                turler = listOf("Role-playing (RPG)", "Adventure"),
                magazaLinkleri = listOf(
                    KaynakMagazaLinki(Magaza.RESMI_SITE, "https://en.bandainamcoent.eu/elden-ring"),
                    KaynakMagazaLinki(Magaza.STEAM, "https://store.steampowered.com/app/1245620"),
                    KaynakMagazaLinki(Magaza.GOG, "https://www.gog.com/game/elden_ring"),
                ),
            ),
            oyunlar[100],
        )
        assertEquals(KaynakTuru.GENISLEME, oyunlar[101]!!.tur)
        assertEquals(100L, oyunlar[101]!!.anaOyunKaynakId)
        assertEquals(100L, oyunlar[102]!!.surumAnasiKaynakId)
        assertEquals(KaynakTuru.DLC, oyunlar[103]!!.tur)
        assertEquals(KaynakTuru.PORT, oyunlar[104]!!.tur)
        assertEquals(KaynakTuru.PAKET, oyunlar[105]!!.tur)
        assertEquals(setOf(100L, 101, 102, 103, 104, 105), oyunlar.keys, "adı olmayan kayıt atlanır")
    }

    @Test
    fun populerlikIgdbZiyaretSayisindanOkunur() = runTest {
        val degerler = kaynak().populerlikSayfasi(0, 500)

        assertEquals(listOf(KaynakPopulerlik(100, 0.0042)), degerler)
        assertEquals("https://api.igdb.com/v4/popularity_primitives", istekler.last().url.toString())
        assertTrue("popularity_type = 1" in istekler.last().govdeMetni)
    }

    @Test
    fun saniyedeDorttenFazlaIstekGonderilmez() = runTest {
        val kaynak = kaynak()

        repeat(9) { kaynak.oyunSayfasi(it.toLong(), 500) }

        // İlk istek hemen, sonraki 8'i 250 ms arayla.
        assertTrue(currentTime >= 2000, "geçen süre: $currentTime ms")
    }

    @Test
    fun suresiDolanTokenYenilenir() = runTest {
        val kaynak = kaynak()
        kaynak.oyunSayfasi(0, 500)

        siradakiOyunIstegiYetkisiz = true
        kaynak.oyunSayfasi(0, 500)

        assertEquals(2, tokenSayisi)
        assertEquals("Bearer token-2", istekler.last().headers[HttpHeaders.Authorization])
    }
}
