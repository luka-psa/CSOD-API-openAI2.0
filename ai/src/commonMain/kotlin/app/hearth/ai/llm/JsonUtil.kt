package app.hearth.ai.llm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal val LlmJson = Json { ignoreUnknownKeys = true }

internal fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
internal fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray
internal fun JsonElement.stringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonObject.str(key: String): String? = get(key)?.takeUnless { it is JsonNull }?.stringOrNull()
internal fun JsonObject.obj(key: String): JsonObject? = get(key)?.jsonObjectOrNull()
internal fun JsonObject.arr(key: String): JsonArray? = get(key)?.jsonArrayOrNull()

/** Parses model text that should be JSON, tolerating a ```json fence around it. */
internal fun parseJsonObjectText(text: String): JsonObject {
    val trimmed = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    return runCatching { LlmJson.parseToJsonElement(trimmed) as JsonObject }.getOrElse {
        throw LlmException(LlmException.Reason.BAD_RESPONSE, "The model did not return valid JSON.")
    }
}
