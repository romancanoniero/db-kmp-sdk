@file:OptIn(ExperimentalJsExport::class)

package com.iyr.db.database.js

import com.iyr.db.core.DbApp
import com.iyr.db.core.DbOptions
import com.iyr.db.database.DataSnapshot
import com.iyr.db.database.DatabaseError
import com.iyr.db.database.DbDatabase
import com.iyr.db.database.ValueEventListener
import com.iyr.db.database.protocol.WsProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.js.Promise

/**
 * Fachada JS-friendly (Promise + callbacks) para consumo desde el CRM React/TS.
 *
 * El target js(IR) del mismo módulo expone API Kotlin/JS; esta fachada la
 * traduce a una forma idiomática JS. El CRM, en src/lib/firebase/, mapea su API
 * con forma Firebase (ref/onValue/set/update) sobre estos métodos.
 *
 * El intercambio de datos es por **JSON string** en el borde (la app TS hace
 * JSON.parse/stringify), para un contrato estable sin fricción de interop.
 */
@JsExport
class DbWeb(endpoint: String, tokenProvider: () -> Promise<String?>) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db: DbDatabase

    init {
        if (!DbApp.isInitialized()) {
            DbApp.initialize(
                DbOptions(
                    endpoint = endpoint,
                    tokenProvider = { tokenProvider().await() },
                ),
            )
        }
        db = DbDatabase.getInstance()
    }

    fun ref(path: String): DbRefWeb = DbRefWeb(db, scope, path)
}

@JsExport
class DbRefWeb internal constructor(
    private val db: DbDatabase,
    private val scope: CoroutineScope,
    private val path: String,
) {
    /** Suscripción en tiempo real. Devuelve una función de baja (unsubscribe). */
    fun onValue(callback: (json: String?) -> Unit): () -> Unit {
        val ref = db.getReference(path)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) = callback(snapshot.toJsonString())
            override fun onCancelled(error: DatabaseError) { /* el caller maneja errores vía estado */ }
        }
        ref.addValueEventListener(listener)
        return { ref.removeEventListener(listener) }
    }

    /** Lectura one-shot. Resuelve con el JSON del nodo (o null). */
    fun get(): Promise<String?> = scope.promise {
        db.getReference(path).get().toJsonString()
    }

    fun set(json: String): Promise<Unit> = scope.promise {
        db.getReference(path).setValue(WsProtocol.json.parseToJsonElement(json))
        Unit
    }

    fun update(json: String): Promise<Unit> = scope.promise {
        val obj = WsProtocol.json.parseToJsonElement(json) as JsonObject
        db.getReference(path).updateChildren(obj.mapValues { it.value as Any? })
        Unit
    }

    fun remove(): Promise<Unit> = scope.promise {
        db.getReference(path).removeValue()
        Unit
    }

    /** push(): crea hijo con push-id, escribe el valor y resuelve con la nueva key. */
    fun push(json: String): Promise<String?> = scope.promise {
        val child = db.getReference(path).push()
        child.setValue(WsProtocol.json.parseToJsonElement(json))
        child.getKey()
    }
}

private fun DataSnapshot.toJsonString(): String? {
    val el: JsonElement = rawElement() ?: return null
    return WsProtocol.json.encodeToString(JsonElement.serializer(), el)
}
