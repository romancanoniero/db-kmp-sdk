package com.iyr.db.database

import com.iyr.db.database.protocol.WsProtocol
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Conversión Any? (Map/List/primitivos, como lo pasa hoy la app) <-> JsonElement,
 * y deserialización tipada de snapshots (paridad con FirebaseModelParser.getValue<T>()).
 */

fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Int -> JsonPrimitive(this)
    is Long -> JsonPrimitive(this)
    is Float -> JsonPrimitive(this)
    is Double -> JsonPrimitive(this)
    is Map<*, *> -> buildJsonObject {
        for ((k, v) in this@toJsonElement) put(k.toString(), v.toJsonElement())
    }
    is Iterable<*> -> buildJsonArray {
        for (v in this@toJsonElement) add(v.toJsonElement())
    }
    else -> JsonPrimitive(this.toString())
}

/** Deserialización tipada del snapshot a un modelo @Serializable. */
inline fun <reified T> DataSnapshot.getValue(): T? {
    val el = rawElement() ?: return null
    if (el is JsonNull) return null
    return runCatching { WsProtocol.json.decodeFromJsonElement<T>(el) }.getOrNull()
}

fun <T> DataSnapshot.getValue(deserializer: DeserializationStrategy<T>): T? {
    val el = rawElement() ?: return null
    if (el is JsonNull) return null
    return runCatching { WsProtocol.json.decodeFromJsonElement(deserializer, el) }.getOrNull()
}

/** Lista tipada de los hijos del snapshot (paridad con getValuesList<T>()). */
inline fun <reified T> DataSnapshot.getValuesList(): List<T> =
    getChildren().mapNotNull { it.getValue<T>() }

internal fun JsonElement.asJsonObjectOrEmpty(): JsonObject =
    this as? JsonObject ?: JsonObject(emptyMap())

internal fun JsonArray.toAnyList(): List<Any?> = map { it.toKotlin() }
