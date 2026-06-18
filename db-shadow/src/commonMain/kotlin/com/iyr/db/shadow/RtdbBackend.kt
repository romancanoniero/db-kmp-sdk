package com.iyr.db.shadow

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement

/**
 * Contrato neutral de acceso a la RTDB, independiente del origen (Firebase o
 * db-kmp-sdk/server). Trabaja en [JsonElement] crudo para no acoplarse a ningún
 * SDK; el adapter de cada cliente convierte hacia/desde su propio DataSnapshot.
 *
 * La capa de negocio NUNCA cambia: el swap ocurre eligiendo qué implementación
 * de [RtdbBackend] se inyecta (Firebase, Server o [ShadowRtdbBackend]).
 */
interface RtdbBackend {
    /** Nombre legible para logs/diffs (p.ej. "firebase", "server"). */
    val name: String

    /** Observa el valor de un path en tiempo real (equivale a addValueEventListener). */
    fun observeValue(path: String): Flow<JsonElement?>

    /** Lectura one-shot (equivale a addListenerForSingleValueEvent / get). */
    suspend fun getValue(path: String): JsonElement?

    /** setValue(value). `null` borra el nodo (semántica Firebase). */
    suspend fun setValue(path: String, value: JsonElement?)

    /** updateChildren(map) multi-path. */
    suspend fun updateChildren(path: String, children: Map<String, JsonElement?>)

    /** removeValue(). */
    suspend fun removeValue(path: String)
}
