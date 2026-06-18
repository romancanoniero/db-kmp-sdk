package com.iyr.db.shadow

import kotlinx.serialization.json.JsonElement

/**
 * Origen de datos efectivo para un path (flag de migración).
 *  - FIREBASE: solo Firebase (comportamiento actual; default).
 *  - SERVER:   solo db-kmp-sdk/VPS (path ya migrado y validado).
 *  - SHADOW:   Firebase sirve al caller; el server corre en paralelo y se compara.
 */
enum class RtdbSource { FIREBASE, SERVER, SHADOW }

/**
 * Configuración de la capa de shadow. Por defecto NO sombrea nada y respeta
 * Firebase como único origen → cero impacto en producción hasta activarlo por
 * path.
 *
 * @property shadowedPaths paths (prefijos) sobre los que se corre la comparación.
 * @property shadowAll fuerza shadow en todos los paths (solo para pruebas).
 * @property mirrorWrites si true, las escrituras del primario se espejan al
 *   secundario (best-effort) para que el server se pueble y la comparación de
 *   lecturas sea significativa. Nunca hace fallar la escritura del primario.
 */
class ShadowConfig(
    val shadowedPaths: Set<String> = emptySet(),
    val shadowAll: Boolean = false,
    val mirrorWrites: Boolean = true,
) {
    fun isShadowed(path: String): Boolean =
        shadowAll || shadowedPaths.any { path == it || path.startsWith("$it/") || it.startsWith("$path/") }
}

/**
 * Resultado de una comparación de paridad entre primario y secundario para un path.
 */
data class ShadowReport(
    val path: String,
    val match: Boolean,
    val primary: JsonElement?,
    val secondary: JsonElement?,
) {
    fun describe(): String =
        if (match) {
            "✅ MATCH  $path"
        } else {
            "❌ MISMATCH  $path\n   primary(firebase)= $primary\n   secondary(server)= $secondary"
        }
}
