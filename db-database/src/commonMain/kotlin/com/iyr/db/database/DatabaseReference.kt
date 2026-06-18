package com.iyr.db.database

import com.iyr.db.database.internal.PushId
import com.iyr.db.database.internal.RealtimeClient
import com.iyr.db.database.protocol.QuerySpec

/**
 * DatabaseReference — referencia a un path concreto del árbol. Misma superficie
 * que la fachada actual de IANKMP/Operators (child/push/setValue/updateChildren/
 * removeValue/get) para que el adapter de cada cliente sea un delegado directo.
 */
class DatabaseReference internal constructor(
    client: RealtimeClient,
    path: String,
) : Query(client, path, QuerySpec()) {

    fun child(path: String): DatabaseReference =
        DatabaseReference(client, joinPath(this.path, path))

    val parent: DatabaseReference?
        get() {
            if (path.isEmpty()) return null
            val idx = path.trimEnd('/').lastIndexOf('/')
            return if (idx <= 0) DatabaseReference(client, "") else DatabaseReference(client, path.substring(0, idx))
        }

    fun getKey(): String? = path.trimEnd('/').substringAfterLast('/').ifEmpty { null }

    /** Genera un push-id ordenable estilo Firebase y devuelve la referencia hija. */
    fun push(): DatabaseReference {
        val id = PushId.generate()
        return DatabaseReference(client, joinPath(path, id))
    }

    suspend fun setValue(value: Any?): DatabaseResult = client.setValue(path, value)

    suspend fun updateChildren(update: Map<String, Any?>): DatabaseResult =
        client.updateChildren(path, update)

    suspend fun removeValue(): DatabaseResult = client.removeValue(path)

    suspend fun get(): DataSnapshot = client.get(path, QuerySpec(), this)

    private fun joinPath(base: String, child: String): String {
        val b = base.trim('/')
        val c = child.trim('/')
        return when {
            b.isEmpty() -> c
            c.isEmpty() -> b
            else -> "$b/$c"
        }
    }
}
