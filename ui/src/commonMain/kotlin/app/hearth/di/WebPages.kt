package app.hearth.di

import app.hearth.core.tools.week.PageFetcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess

/**
 * Web pages for a recipe import. The tablet fetches the page itself; a browser may not read other sites,
 * so the web preview asks the relay, which hands back only the recipe data and the photo.
 */
class WebPages(private val http: HttpClient, private val direct: Boolean, private val relay: suspend () -> String?) : PageFetcher {
    override suspend fun page(url: String): String = (get(url, "page") ?: error("Page not found")).decodeToString()

    override suspend fun image(url: String): ByteArray? = get(url, "image")

    private suspend fun get(url: String, kind: String): ByteArray? {
        val address = if (direct) url else "${relay()?.trimEnd('/') ?: error("No relay")}/$kind?url=${url.encodeURLParameter()}"
        val res = http.get(address) { if (direct) header("User-Agent", "Mozilla/5.0 (Hearth recipe import)") }
        return if (res.status.isSuccess()) res.bodyAsBytes() else null
    }
}
