package app.hearth.core.tools

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Derives a JSON Schema (the subset LLM tool-calling and MCP use) from a serializable class,
 * so argument classes stay the only definition of a tool's input.
 */
@OptIn(ExperimentalSerializationApi::class)
fun SerialDescriptor.toJsonSchema(description: String? = null): JsonObject = buildJsonObject {
    when (val k = kind) {
        StructureKind.CLASS, StructureKind.OBJECT -> {
            put("type", "object")
            putJsonObject("properties") {
                for (i in 0 until elementsCount) {
                    val desc = getElementAnnotations(i).filterIsInstance<Describe>().firstOrNull()?.text
                    put(getElementName(i), getElementDescriptor(i).toJsonSchema(desc))
                }
            }
            val required = (0 until elementsCount).filterNot { isElementOptional(it) }
            if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(getElementName(it)) } }
            put("additionalProperties", false)
        }
        StructureKind.LIST -> {
            put("type", "array")
            put("items", getElementDescriptor(0).toJsonSchema())
        }
        SerialKind.ENUM -> {
            put("type", "string")
            putJsonArray("enum") { (0 until elementsCount).forEach { add(getElementName(it)) } }
        }
        PrimitiveKind.STRING, PrimitiveKind.CHAR -> put("type", "string")
        PrimitiveKind.BOOLEAN -> put("type", "boolean")
        PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> put("type", "integer")
        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> put("type", "number")
        is PolymorphicKind, StructureKind.MAP, SerialKind.CONTEXTUAL ->
            error("Tool arguments must be plain classes, lists, enums or primitives (got $k in $serialName)")
    }
    (description ?: annotations.filterIsInstance<Describe>().firstOrNull()?.text)?.let { put("description", it) }
}
