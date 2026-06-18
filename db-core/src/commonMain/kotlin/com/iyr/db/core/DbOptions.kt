package com.iyr.db.core

/**
 * Configuración de conexión al backend propio (db-realtime-gateway).
 *
 * Equivalente conceptual a `FirebaseOptions`, pero en lugar de apuntar a un
 * proyecto Firebase apunta a la URL del gateway WebSocket en el VPS.
 *
 * @property endpoint URL base del gateway, p.ej. "wss://db.midominio.com/db".
 * @property tokenProvider función suspend que devuelve el ID token vigente con
 *   el que el gateway autentica al usuario (Fase 1: ID token de Firebase Auth,
 *   verificado server-side por JWKS).
 * @property persistenceEnabled habilita cache local (paridad con keepSynced).
 */
class DbOptions(
    val endpoint: String,
    val tokenProvider: suspend () -> String?,
    val persistenceEnabled: Boolean = true,
) {
    class Builder {
        private var endpoint: String? = null
        private var tokenProvider: (suspend () -> String?) = { null }
        private var persistenceEnabled: Boolean = true

        fun setEndpoint(endpoint: String) = apply { this.endpoint = endpoint }
        fun setTokenProvider(provider: suspend () -> String?) = apply { this.tokenProvider = provider }
        fun setPersistenceEnabled(enabled: Boolean) = apply { this.persistenceEnabled = enabled }

        fun build(): DbOptions {
            val ep = requireNotNull(endpoint) { "DbOptions.endpoint es obligatorio" }
            return DbOptions(ep, tokenProvider, persistenceEnabled)
        }
    }
}
