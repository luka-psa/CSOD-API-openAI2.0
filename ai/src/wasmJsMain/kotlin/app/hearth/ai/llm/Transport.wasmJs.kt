package app.hearth.ai.llm

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js

actual fun defaultTransport(): HttpTransport = KtorTransport(HttpClient(Js) { configureHearth() })
