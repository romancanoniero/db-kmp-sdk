package com.iyr.db.database.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Protocolo WebSocket db-kmp-sdk <-> db-realtime-gateway.
 *
 * Discriminador de tipo = campo "t" (igual idea que el wire protocol de RTDB,
 * pero propio y serializado con kotlinx.serialization para compartir los mismos
 * modelos @Serializable en cliente KMP y gateway Ktor/JVM).
 *
 * Mensajes cliente -> servidor: [ClientMessage].
 * Mensajes servidor -> cliente: [ServerMessage].
 *
 * Semántica de eventos = paridad con Firebase RTDB:
 *  - ValueEvent  -> [ServerMessage.Value]
 *  - ChildAdded/Changed/Removed/Moved -> [ServerMessage.Child*]
 */
object WsProtocol {
    /** Json compartido. SnakeCase para alinear con los datos snake_case del RTDB actual. */
    val json: Json = Json {
        classDiscriminator = "t"
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }
}

/**
 * Especificación de query (paridad con Query de Firebase: orderBy* + filtros + limit).
 * `value` se transporta como JsonElement para soportar String/Double/Boolean/null.
 */
@Serializable
data class QuerySpec(
    val orderByChild: String? = null,
    val orderByKey: Boolean = false,
    val orderByValue: Boolean = false,
    val equalTo: JsonElement? = null,
    val equalToKey: String? = null,
    val startAt: JsonElement? = null,
    val startAtKey: String? = null,
    val endAt: JsonElement? = null,
    val endAtKey: String? = null,
    val limitToFirst: Long? = null,
    val limitToLast: Long? = null,
) {
    fun isEmpty(): Boolean =
        orderByChild == null && !orderByKey && !orderByValue &&
            equalTo == null && startAt == null && endAt == null &&
            limitToFirst == null && limitToLast == null
}

// ---------------------------------------------------------------------------
// Cliente -> Servidor
// ---------------------------------------------------------------------------

@Serializable
sealed interface ClientMessage

/** Autenticación de la sesión WS con el ID token vigente (verificado por JWKS en el gateway). */
@Serializable
@SerialName("auth")
data class AuthMessage(val token: String) : ClientMessage

/** Suscripción a un path (con query opcional). `id` identifica la suscripción lógica. */
@Serializable
@SerialName("listen")
data class ListenMessage(val id: Long, val path: String, val q: QuerySpec? = null) : ClientMessage

/** Cancelación de una suscripción previa. */
@Serializable
@SerialName("unlisten")
data class UnlistenMessage(val id: Long) : ClientMessage

/** Lectura one-shot (addListenerForSingleValueEvent / get). `rid` correlaciona la respuesta. */
@Serializable
@SerialName("get")
data class GetMessage(val rid: Long, val path: String, val q: QuerySpec? = null) : ClientMessage

/** setValue(value) sobre un path. */
@Serializable
@SerialName("set")
data class SetMessage(val rid: Long, val path: String, val value: JsonElement) : ClientMessage

/** updateChildren(map) atómico multi-path. */
@Serializable
@SerialName("update")
data class UpdateMessage(val rid: Long, val path: String, val value: JsonObject) : ClientMessage

/** removeValue() sobre un path. */
@Serializable
@SerialName("remove")
data class RemoveMessage(val rid: Long, val path: String) : ClientMessage

/** push(): el cliente genera el push-id ordenable y lo envía como `key`. */
@Serializable
@SerialName("push")
data class PushMessage(val rid: Long, val path: String, val key: String, val value: JsonElement) : ClientMessage

/** onDisconnect: acción a aplicar por el gateway al caerse el socket. */
@Serializable
@SerialName("on_disconnect")
data class OnDisconnectMessage(
    val path: String,
    val op: OnDisconnectOp,
    val value: JsonElement? = null,
) : ClientMessage

@Serializable
enum class OnDisconnectOp { SET, UPDATE, REMOVE, CANCEL }

// ---------------------------------------------------------------------------
// Servidor -> Cliente
// ---------------------------------------------------------------------------

@Serializable
sealed interface ServerMessage

/** Snapshot completo del path suscripto (estado inicial y refrescos posteriores). */
@Serializable
@SerialName("value")
data class ValueEvent(val id: Long, val snapshot: JsonElement?) : ServerMessage

@Serializable
@SerialName("child_added")
data class ChildAddedEvent(val id: Long, val key: String, val prev: String?, val value: JsonElement?) : ServerMessage

@Serializable
@SerialName("child_changed")
data class ChildChangedEvent(val id: Long, val key: String, val prev: String?, val value: JsonElement?) : ServerMessage

@Serializable
@SerialName("child_removed")
data class ChildRemovedEvent(val id: Long, val key: String, val value: JsonElement?) : ServerMessage

@Serializable
@SerialName("child_moved")
data class ChildMovedEvent(val id: Long, val key: String, val prev: String?, val value: JsonElement?) : ServerMessage

/** Confirmación de una operación de escritura/lectura correlacionada por `rid`. */
@Serializable
@SerialName("ack")
data class AckMessage(val rid: Long, val snapshot: JsonElement? = null) : ServerMessage

/** Error correlacionado (rid) o de suscripción (id). */
@Serializable
@SerialName("error")
data class ErrorMessage(
    val rid: Long? = null,
    val id: Long? = null,
    val code: String,
    val message: String,
) : ServerMessage
