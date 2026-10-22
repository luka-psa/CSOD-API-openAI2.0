package app.hearth.ai.llm

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal fun HttpClientConfig<*>.configureHearth() {
    expectSuccess = false
    install(HttpTimeout) {
        requestTimeoutMillis = 90_000
        connectTimeoutMillis = 15_000
    }
}

class KtorTransport(private val client: HttpClient) : HttpTransport {
    override suspend fun postJson(url: String, headers: Map<String, String>, body: String): HttpResult {
        val response = client.post(url) {
            headers.forEach { (k, v) -> header(k, v) }
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        return HttpResult(response.status.value, response.bodyAsText())
    }
}
