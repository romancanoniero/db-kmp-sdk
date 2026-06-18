package com.iyr.db.database

import com.iyr.db.database.internal.RealtimeClient
import com.iyr.db.database.protocol.QuerySpec
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * Query — superficie idéntica a la fachada actual de IANKMP/Operators y a la de
 * firebase-kmp-sdk. Cada filtro devuelve una nueva Query inmutable; la ejecución
 * real ocurre al registrar un listener o llamar a get().
 */
open class Query internal constructor(
    internal val client: RealtimeClient,
    internal val path: String,
    internal val spec: QuerySpec,
) {
    fun orderByChild(field: String): Query = copy(spec.copy(orderByChild = field))
    fun orderByKey(): Query = copy(spec.copy(orderByKey = true))
    fun orderByValue(): Query = copy(spec.copy(orderByValue = true))

    fun equalTo(value: String?, key: String? = null): Query =
        copy(spec.copy(equalTo = value.toJson(), equalToKey = key))
    fun equalTo(value: Double, key: String? = null): Query =
        copy(spec.copy(equalTo = JsonPrimitive(value), equalToKey = key))
    fun equalTo(value: Boolean, key: String? = null): Query =
        copy(spec.copy(equalTo = JsonPrimitive(value), equalToKey = key))

    fun startAt(value: String?, key: String? = null): Query =
        copy(spec.copy(startAt = value.toJson(), startAtKey = key))
    fun startAt(value: Double, key: String? = null): Query =
        copy(spec.copy(startAt = JsonPrimitive(value), startAtKey = key))
    fun startAt(value: Boolean, key: String? = null): Query =
        copy(spec.copy(startAt = JsonPrimitive(value), startAtKey = key))

    fun endAt(value: String?, key: String? = null): Query =
        copy(spec.copy(endAt = value.toJson(), endAtKey = key))
    fun endAt(value: Double, key: String? = null): Query =
        copy(spec.copy(endAt = JsonPrimitive(value), endAtKey = key))
    fun endAt(value: Boolean, key: String? = null): Query =
        copy(spec.copy(endAt = JsonPrimitive(value), endAtKey = key))

    fun limitToFirst(limit: Long): Query = copy(spec.copy(limitToFirst = limit))
    fun limitToLast(limit: Long): Query = copy(spec.copy(limitToLast = limit))

    /**
     * Paridad con keepSynced de Firebase. En este SDK la persistencia se controla
     * vía DbOptions.persistenceEnabled; este método queda como hint de caching.
     */
    fun keepSynced(enabled: Boolean) {
        client.setKeepSynced(path, spec, enabled)
    }

    fun addValueEventListener(listener: ValueEventListener) {
        client.addValueEventListener(path, spec, listener)
    }

    fun addChildEventListener(listener: ChildEventListener) {
        client.addChildEventListener(path, spec, listener)
    }

    fun removeEventListener(listener: ValueEventListener) {
        client.removeValueEventListener(listener)
    }

    fun removeEventListener(listener: ChildEventListener) {
        client.removeChildEventListener(listener)
    }

    /** Lectura one-shot (paridad con addListenerForSingleValueEvent). */
    suspend fun addListenerForSingleValueEvent(): DataSnapshot =
        client.get(path, spec, asReference())

    /** Observación reactiva idiomática KMP (paridad con valueEvents: Flow de firebase-kmp-sdk). */
    val valueEvents: Flow<DataSnapshot>
        get() = client.valueEventsFlow(path, spec, asReference())

    private fun copy(newSpec: QuerySpec): Query = Query(client, path, newSpec)

    internal fun asReference(): DatabaseReference = DatabaseReference(client, path)

    private fun String?.toJson(): JsonElement =
        if (this == null) JsonPrimitive(null as String?) else JsonPrimitive(this)
}
