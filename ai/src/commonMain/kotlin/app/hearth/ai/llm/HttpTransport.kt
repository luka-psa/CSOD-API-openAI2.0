package app.hearth.ai.llm

/** Minimal HTTP surface the providers need; faked in tests, Ktor in the app. */
fun interface HttpTransport {
    suspend fun postJson(url: String, headers: Map<String, String>, body: String): HttpResult
}

data class HttpResult(val status: Int, val body: String)

expect fun defaultTransport(): HttpTransport

internal suspend fun HttpTransport.send(url: String, headers: Map<String, String>, body: String): String {
    val result = try {
        postJson(url, headers, body)
    } catch (e: LlmException) {
        throw e
    } catch (e: Exception) {
        throw LlmException(LlmException.Reason.NETWORK, e.message ?: "Network error")
    }
    return when (result.status) {
        in 200..299 -> result.body
        401, 403 -> throw LlmException(LlmException.Reason.AUTH, errorMessage(result.body) ?: "The API key was not accepted.")
        429 -> throw LlmException(LlmException.Reason.RATE_LIMIT, errorMessage(result.body) ?: "Too many requests right now.")
        in 400..499 -> throw LlmException(LlmException.Reason.BAD_REQUEST, errorMessage(result.body) ?: "Request rejected (${result.status}).")
        else -> throw LlmException(LlmException.Reason.NETWORK, errorMessage(result.body) ?: "Provider unavailable (${result.status}).")
    }
}

/** Pulls `error.message` out of OpenAI-, xAI- and Anthropic-style error bodies. */
private fun errorMessage(body: String): String? = runCatching {
    val error = LlmJson.parseToJsonElement(body).jsonObjectOrNull()?.get("error")
    error?.jsonObjectOrNull()?.get("message")?.stringOrNull() ?: error?.stringOrNull()
}.getOrNull()
