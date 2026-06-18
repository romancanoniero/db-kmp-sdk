package com.iyr.db.shadow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/**
 * [RtdbBackend] en memoria que emula la semántica de árbol de Firebase RTDB.
 *
 * Pensado para tests/demos y como *stand-in* del backend Firebase real cuando se
 * quiere ejercitar la capa de shadow sin un proyecto Firebase. El backend
 * Firebase de producción será un delegado equivalente sobre
 * `FirebaseRealtimeDatabaseService` del cliente.
 */
class InMemoryRtdbBackend(override val name: String = "firebase") : RtdbBackend {

    private val root = MutableStateFlow<JsonElement>(JsonObject(emptyMap()))

    override fun observeValue(path: String): Flow<JsonElement?> =
        root.map { navigate(it, path) }

    override suspend fun getValue(path: String): JsonElement? = navigate(root.value, path)

    override suspend fun setValue(path: String, value: JsonElement?) {
        root.update { setIn(it, segments(path), value) }
    }

    override suspend fun updateChildren(path: String, children: Map<String, JsonElement?>) {
        root.update { current ->
            var acc = current
            for ((k, v) in children) acc = setIn(acc, segments("$path/$k"), v)
            acc
        }
    }

    override suspend fun removeValue(path: String) {
        root.update { setIn(it, segments(path), null) }
    }

    private fun segments(path: String): List<String> = path.split('/').filter { it.isNotEmpty() }

    private fun navigate(rootEl: JsonElement, path: String): JsonElement? {
        var current: JsonElement? = rootEl
        for (s in segments(path)) current = (current as? JsonObject)?.get(s) ?: return null
        return current
    }

    private fun setIn(rootEl: JsonElement, segs: List<String>, value: JsonElement?): JsonElement {
        if (segs.isEmpty()) return value ?: JsonObject(emptyMap())
        val map = (rootEl as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        val key = segs.first()
        if (segs.size == 1) {
            if (value == null || value is JsonNull) map.remove(key) else map[key] = value
        } else {
            map[key] = setIn(map[key] ?: JsonObject(emptyMap()), segs.drop(1), value)
        }
        return JsonObject(map)
    }
}
