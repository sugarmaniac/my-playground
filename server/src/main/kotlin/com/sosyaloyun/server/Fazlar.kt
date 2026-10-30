package com.sosyaloyun.server

import com.sosyaloyun.shared.ApiHatasi
import com.sosyaloyun.shared.Faz
import com.sosyaloyun.shared.HataKodu
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext

/** Açık faz (#45, #46). Fazlar birikimlidir: Faz 3 açıkken Faz 2 de açıktır. Kapatılan fazın verisi silinmez. */
class Fazlar(private val ayarlar: Ayarlar) {
    suspend fun aktif(): Faz = Faz.seviyeden(ayarlar.oku(Ayar.AktifFaz))

    suspend fun acik(gereken: Faz): Boolean = aktif().kapsar(gereken)

    suspend fun degistir(faz: Faz) = ayarlar.yaz(Ayar.AktifFaz, faz.seviye)
}

/**
 * İçindeki uç noktalar yalnızca [gereken] faz açıkken çalışır; kapalıyken 404 `ozellik_kapali` döner. Denetim her
 * istekte backend'de yapılır, istemcinin arayüzü gizlemesine güvenilmez.
 */
fun Route.fazGerekli(fazlar: Fazlar, gereken: Faz, govde: Route.() -> Unit) {
    val dal = createChild(FazSecici(gereken))
    dal.install(FazDenetimi) {
        this.fazlar = fazlar
        this.gereken = gereken
    }
    dal.govde()
}

class FazDenetimiAyari {
    lateinit var fazlar: Fazlar
    var gereken: Faz = Faz.FAZ_1
}

private val FazDenetimi = createRouteScopedPlugin("FazDenetimi", ::FazDenetimiAyari) {
    val fazlar = pluginConfig.fazlar
    val gereken = pluginConfig.gereken
    onCall { call ->
        if (!fazlar.acik(gereken)) call.respond(HttpStatusCode.NotFound, ApiHatasi(HataKodu.OZELLIK_KAPALI))
    }
}

/** Yol eşleşmesine katılmayan, yalnızca faz denetimini taşıyan dal. */
private class FazSecici(private val faz: Faz) : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) = RouteSelectorEvaluation.Transparent

    override fun toString(): String = "(faz ${faz.seviye})"
}
