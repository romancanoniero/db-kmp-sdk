package com.iyr.db.database

import com.iyr.db.core.DbApp
import com.iyr.db.database.internal.RealtimeClient

/**
 * Entrypoint del servicio de base de datos en tiempo real (equivalente a
 * `FirebaseDatabase` / `FirebaseRealtimeDatabaseService`).
 *
 * Uso (igual que Firebase):
 * ```
 * val ref = DbDatabase.getInstance().getReference("events_followers/EVT")
 * ref.addValueEventListener(object : ValueEventListener { ... })
 * ```
 */
class DbDatabase internal constructor(
    internal val client: RealtimeClient,
) {
    fun getReference(path: String? = null): DatabaseReference =
        DatabaseReference(client, path?.trim('/') ?: "")

    fun goOnline() = client.goOnline()
    fun goOffline() = client.goOffline()

    /** Cierra la conexión y libera recursos (teardown/logout). */
    fun close() {
        client.shutdown()
        instances.entries.removeAll { it.value === this }
    }

    companion object {
        private val instances = mutableMapOf<String, DbDatabase>()

        fun getInstance(app: DbApp = DbApp.getInstance()): DbDatabase =
            instances.getOrPut(app.name) {
                DbDatabase(RealtimeClient(app.options))
            }
    }
}
