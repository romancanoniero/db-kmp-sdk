package com.iyr.db.database

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Snapshot inmutable de un nodo. Envuelve un [JsonElement] (el valor crudo que
 * envía el gateway) y expone la MISMA superficie que el DataSnapshot de Firebase
 * que consumen hoy los clientes:
 *
 *  - getValue(): Any?  -> Map<String,Any?> / List<Any?> / String / Double / Boolean / null
 *  - getChildren(), child(path), hasChild(path), hasChildren(), exists(), key()
 *
 * Mantener `getValue()` devolviendo Map/List/primitivos es lo que permite que
 * los ~200 cast manuales y `FirebaseModelParser.getValue<T>()` sigan andando
 * sin cambios en la app.
 */
class DataSnapshot internal constructor(
    private val nodeKey: String?,
    private val element: JsonElement?,
    val ref: DatabaseReference,
) {
    fun key(): String? = nodeKey

    fun exists(): Boolean = element != null && element !is JsonNull

    fun hasChildren(): Boolean = (element as? JsonObject)?.isNotEmpty() == true

    fun hasChild(path: String): Boolean = navigate(element, path) != null

    fun child(path: String): DataSnapshot {
        val childKey = path.substringAfterLast('/')
        return DataSnapshot(childKey, navigate(element, path), ref.child(path))
    }

    fun getChildren(): Iterable<DataSnapshot> {
        val obj = element as? JsonObject ?: return emptyList()
        return obj.entries.map { (k, v) -> DataSnapshot(k, v, ref.child(k)) }
    }

    /** Valor crudo convertido a tipos Kotlin nativos (idéntico a Firebase). */
    fun getValue(): Any? = element?.toKotlin()

    /** Alias histórico usado por la fachada de IANKMP. */
    fun value(): Any? = getValue()

    /** Acceso al [JsonElement] crudo para deserialización tipada (ver SnapshotSerialization). */
    fun rawElement(): JsonElement? = element

    private fun navigate(root: JsonElement?, path: String): JsonElement? {
        var current: JsonElement? = root
        for (segment in path.split('/').filter { it.isNotEmpty() }) {
            current = (current as? JsonObject)?.get(segment) ?: return null
        }
        return current
    }
}

internal fun JsonElement.toKotlin(): Any? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        doubleOrNull != null -> doubleOrNull
        else -> content
    }
    is JsonObject -> entries.associate { (k, v) -> k to v.toKotlin() }
    is JsonArray -> map { it.toKotlin() }
}
