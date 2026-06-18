package com.iyr.db.shadow

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.math.floor

/**
 * Normalización CANÓNICA para comparar paridad Firebase ↔ server sin falsos
 * positivos. Reglas:
 *  - objetos: claves ordenadas alfabéticamente (Firebase no garantiza orden de
 *    serialización; el orden lógico se compara aparte si hace falta);
 *  - números: enteros y dobles con parte entera (1000 vs 1000.0) se unifican,
 *    para que la representación de tipo no dispare un diff espurio;
 *  - null y objeto/array vacío se distinguen (un nodo ausente NO es lo mismo que
 *    uno vacío en RTDB, y queremos ver ese diff).
 *
 * Si dos snapshots canónicos son `==`, son equivalentes a ojos del consumidor.
 */
fun canonicalize(element: JsonElement?): JsonElement? = when (element) {
    null, is JsonNull -> null
    is JsonObject -> JsonObject(
        element.entries
            .sortedBy { it.key }
            .associate { (k, v) -> k to (canonicalize(v) ?: JsonNull) },
    )
    is JsonArray -> JsonArray(element.map { canonicalize(it) ?: JsonNull })
    is JsonPrimitive -> canonicalPrimitive(element)
}

private fun canonicalPrimitive(p: JsonPrimitive): JsonPrimitive {
    if (p.isString) return p
    p.longOrNull?.let { return JsonPrimitive(it) }
    p.doubleOrNull?.let { d ->
        return if (!d.isNaN() && !d.isInfinite() && d == floor(d)) JsonPrimitive(d.toLong())
        else JsonPrimitive(d)
    }
    p.booleanOrNull?.let { return JsonPrimitive(it) }
    return p
}

/**
 * Convierte un [JsonElement] a tipos Kotlin nativos (Map/List/String/Long/Double/
 * Boolean/null) para poder pasarlo a APIs estilo Firebase que reciben `Any?`
 * (db-kmp-sdk `setValue(Any?)`).
 */
fun jsonElementToAny(element: JsonElement?): Any? = when (element) {
    null, is JsonNull -> null
    is JsonPrimitive -> when {
        element.isString -> element.content
        element.booleanOrNull != null -> element.booleanOrNull
        element.longOrNull != null -> element.longOrNull
        element.doubleOrNull != null -> element.doubleOrNull
        else -> element.content
    }
    is JsonObject -> element.mapValues { jsonElementToAny(it.value) }
    is JsonArray -> element.map { jsonElementToAny(it) }
}
