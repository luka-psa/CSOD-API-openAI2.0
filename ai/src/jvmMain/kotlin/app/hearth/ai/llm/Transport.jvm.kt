package app.hearth.ai.llm

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun defaultTransport(): HttpTransport = KtorTransport(HttpClient(OkHttp) { configureHearth() })
