package com.iyr.db.shadow

import com.iyr.db.database.DbDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement

/**
 * [RtdbBackend] respaldado por db-kmp-sdk (server propio sobre WebSocket).
 * Es un delegado fino sobre [DbDatabase]: mismas operaciones que la RTDB de
 * Firebase, devolviendo el [JsonElement] crudo del snapshot.
 */
class ServerRtdbBackend(
    private val db: DbDatabase,
    override val name: String = "server",
) : RtdbBackend {

    override fun observeValue(path: String): Flow<JsonElement?> =
        db.getReference(path).valueEvents.map { it.rawElement() }

    override suspend fun getValue(path: String): JsonElement? =
        db.getReference(path).get().rawElement()

    override suspend fun setValue(path: String, value: JsonElement?) {
        db.getReference(path).setValue(jsonElementToAny(value))
    }

    override suspend fun updateChildren(path: String, children: Map<String, JsonElement?>) {
        db.getReference(path).updateChildren(children.mapValues { jsonElementToAny(it.value) })
    }

    override suspend fun removeValue(path: String) {
        db.getReference(path).removeValue()
    }
}
