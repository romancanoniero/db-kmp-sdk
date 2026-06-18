package com.iyr.db.database.internal

import com.iyr.db.core.DbOptions
import com.iyr.db.database.ChildEventListener
import com.iyr.db.database.DataSnapshot
import com.iyr.db.database.DatabaseError
import com.iyr.db.database.DatabaseReference
import com.iyr.db.database.DatabaseResult
import com.iyr.db.database.ValueEventListener
import com.iyr.db.database.protocol.AuthMessage
import com.iyr.db.database.protocol.AckMessage
import com.iyr.db.database.protocol.ChildAddedEvent
import com.iyr.db.database.protocol.ChildChangedEvent
import com.iyr.db.database.protocol.ChildMovedEvent
import com.iyr.db.database.protocol.ChildRemovedEvent
import com.iyr.db.database.protocol.ClientMessage
import com.iyr.db.database.protocol.ErrorMessage
import com.iyr.db.database.protocol.GetMessage
import com.iyr.db.database.protocol.ListenMessage
import com.iyr.db.database.protocol.QuerySpec
import com.iyr.db.database.protocol.RemoveMessage
import com.iyr.db.database.protocol.ServerMessage
import com.iyr.db.database.protocol.SetMessage
import com.iyr.db.database.protocol.UnlistenMessage
import com.iyr.db.database.protocol.UpdateMessage
import com.iyr.db.database.protocol.ValueEvent
import com.iyr.db.database.protocol.WsProtocol
import com.iyr.db.database.toJsonElement
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * Cliente WebSocket multiplexado (una sola conexión por proceso).
 *
 * ESTADO: scaffold funcional de Fase 0. Implementa conexión, auth, suscripciones
 * value/child, lectura one-shot y escrituras con ack. Pendiente de endurecer:
 *  - TODO: backoff configurable + jitter + límite de reintentos.
 *  - TODO: thread-safety estricta de los registros (hoy single-writer asumido).
 *  - TODO: cola de writes durable + optimistic updates + cache offline (keepSynced).
 *  - TODO: dedupe de suscripciones por (path, spec) con refcount.
 */
internal class RealtimeClient(
    private val options: DbOptions,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client: HttpClient by lazy { platformWebSocketClient() }

    private val outgoing = Channel<ClientMessage>(Channel.UNLIMITED)

    private data class Subscription(
        val id: Long,
        val path: String,
        val spec: QuerySpec,
        val valueListener: ValueEventListener? = null,
        val childListener: ChildEventListener? = null,
    )

    private val subscriptions = mutableMapOf<Long, Subscription>()
    private val pendingAcks = mutableMapOf<Long, CompletableDeferred<AckResult>>()

    // Single-writer asumido en Fase 0 (ver TODO de thread-safety).
    private var nextSubId = 1L
    private var nextRid = 1L
    private var started = false
    private var clientStarted = false

    private sealed class AckResult {
        data class Ok(val snapshot: JsonElement?) : AckResult()
        data class Err(val code: String, val message: String) : AckResult()
    }

    // ----------------------------------------------------------------- listeners

    fun addValueEventListener(path: String, spec: QuerySpec, listener: ValueEventListener) {
        val id = nextSubId++
        subscriptions[id] = Subscription(id, path, spec, valueListener = listener)
        ensureStarted()
        outgoing.trySend(ListenMessage(id, path, spec.takeUnless { it.isEmpty() }))
    }

    fun addChildEventListener(path: String, spec: QuerySpec, listener: ChildEventListener) {
        val id = nextSubId++
        subscriptions[id] = Subscription(id, path, spec, childListener = listener)
        ensureStarted()
        outgoing.trySend(ListenMessage(id, path, spec.takeUnless { it.isEmpty() }))
    }

    fun removeValueEventListener(listener: ValueEventListener) {
        val entry = subscriptions.entries.firstOrNull { it.value.valueListener === listener } ?: return
        subscriptions.remove(entry.key)
        outgoing.trySend(UnlistenMessage(entry.key))
    }

    fun removeChildEventListener(listener: ChildEventListener) {
        val entry = subscriptions.entries.firstOrNull { it.value.childListener === listener } ?: return
        subscriptions.remove(entry.key)
        outgoing.trySend(UnlistenMessage(entry.key))
    }

    fun valueEventsFlow(path: String, spec: QuerySpec, ref: DatabaseReference): Flow<DataSnapshot> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { trySend(snapshot) }
            override fun onCancelled(error: DatabaseError) { close(error.toException()) }
        }
        addValueEventListener(path, spec, listener)
        awaitClose { removeValueEventListener(listener) }
    }

    fun setKeepSynced(path: String, spec: QuerySpec, enabled: Boolean) {
        // Fase 0: hint no-op. La persistencia se controla por DbOptions.persistenceEnabled.
    }

    // ----------------------------------------------------------------- one-shot

    suspend fun get(path: String, spec: QuerySpec, ref: DatabaseReference): DataSnapshot {
        val result = request { rid -> GetMessage(rid, path, spec.takeUnless { it.isEmpty() }) }
        val snapshot = (result as? AckResult.Ok)?.snapshot
        return DataSnapshot(path.substringAfterLast('/').ifEmpty { null }, snapshot, ref)
    }

    suspend fun setValue(path: String, value: Any?): DatabaseResult =
        toResult(request { rid -> SetMessage(rid, path, value.toJsonElement()) })

    suspend fun updateChildren(path: String, update: Map<String, Any?>): DatabaseResult {
        val obj: JsonObject = buildJsonObject {
            for ((k, v) in update) put(k, v.toJsonElement())
        }
        return toResult(request { rid -> UpdateMessage(rid, path, obj) })
    }

    suspend fun removeValue(path: String): DatabaseResult =
        toResult(request { rid -> RemoveMessage(rid, path) })

    private suspend fun request(builder: (Long) -> ClientMessage): AckResult {
        val rid = nextRid++
        val deferred = CompletableDeferred<AckResult>()
        pendingAcks[rid] = deferred
        ensureStarted()
        outgoing.trySend(builder(rid))
        return deferred.await()
    }

    private fun toResult(ack: AckResult): DatabaseResult = when (ack) {
        is AckResult.Ok -> DatabaseResult.Success()
        is AckResult.Err -> DatabaseResult.Error(ack.message)
    }

    // ----------------------------------------------------------------- connection

    fun goOnline() = ensureStarted()
    fun goOffline() { /* TODO Fase 1: pausar conexión y reanudar bajo demanda. */ }

    private fun ensureStarted() {
        if (started) return
        started = true
        clientStarted = true
        scope.launch { connectionLoop() }
    }

    /**
     * Cierra la conexión, cancela las corrutinas internas y libera el engine HTTP.
     * Necesario para liberar recursos (tests, logout, teardown del proceso).
     */
    fun shutdown() {
        runCatching { scope.cancel() }
        if (clientStarted) runCatching { client.close() }
    }

    private suspend fun connectionLoop() {
        var backoffMs = 500L
        while (scope.isActive) {
            try {
                val session = client.webSocketSession(urlString = options.endpoint)
                backoffMs = 500L
                options.tokenProvider()?.let { session.sendMessage(AuthMessage(it)) }
                // Resync: re-suscribir todo lo activo tras (re)conectar.
                subscriptions.values.forEach { sub ->
                    session.sendMessage(ListenMessage(sub.id, sub.path, sub.spec.takeUnless { it.isEmpty() }))
                }
                val senderJob = scope.launch {
                    for (msg in outgoing) session.sendMessage(msg)
                }
                try {
                    for (frame in session.incoming) {
                        if (frame is Frame.Text) handleServerText(frame.readText())
                    }
                } finally {
                    senderJob.cancel()
                }
            } catch (_: Throwable) {
                // TODO: clasificar errores; cancelar pendingAcks que aplique.
            }
            if (!scope.isActive) break
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(15_000L)
        }
    }

    private suspend fun DefaultClientWebSocketSession.sendMessage(msg: ClientMessage) {
        send(Frame.Text(WsProtocol.json.encodeToString(ClientMessage.serializer(), msg)))
    }

    private fun handleServerText(text: String) {
        val msg = runCatching { WsProtocol.json.decodeFromString(ServerMessage.serializer(), text) }.getOrNull() ?: return
        when (msg) {
            is ValueEvent -> {
                val sub = subscriptions[msg.id] ?: return
                val ref = DatabaseReference(this, sub.path)
                sub.valueListener?.onDataChange(
                    DataSnapshot(sub.path.substringAfterLast('/').ifEmpty { null }, msg.snapshot, ref),
                )
            }
            is ChildAddedEvent -> dispatchChild(msg.id, msg.key, msg.value) { sub, snap -> sub.childListener?.onChildAdded(snap, msg.prev) }
            is ChildChangedEvent -> dispatchChild(msg.id, msg.key, msg.value) { sub, snap -> sub.childListener?.onChildChanged(snap, msg.prev) }
            is ChildRemovedEvent -> dispatchChild(msg.id, msg.key, msg.value) { sub, snap -> sub.childListener?.onChildRemoved(snap) }
            is ChildMovedEvent -> dispatchChild(msg.id, msg.key, msg.value) { sub, snap -> sub.childListener?.onChildMoved(snap, msg.prev) }
            is AckMessage -> pendingAcks.remove(msg.rid)?.complete(AckResult.Ok(msg.snapshot))
            is ErrorMessage -> {
                val rid = msg.rid
                if (rid != null) {
                    pendingAcks.remove(rid)?.complete(AckResult.Err(msg.code, msg.message))
                } else {
                    val sub = msg.id?.let { subscriptions[it] }
                    val err = DatabaseError(code = -1, message = msg.message, details = msg.code)
                    sub?.valueListener?.onCancelled(err)
                    sub?.childListener?.onCancelled(err)
                }
            }
        }
    }

    private inline fun dispatchChild(
        id: Long,
        key: String,
        value: JsonElement?,
        block: (Subscription, DataSnapshot) -> Unit,
    ) {
        val sub = subscriptions[id] ?: return
        val ref = DatabaseReference(this, "${sub.path}/$key")
        block(sub, DataSnapshot(key, value, ref))
    }
}
