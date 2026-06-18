package com.iyr.db.shadow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement

/**
 * Backend de MIGRACIÓN SEGURA. Sirve siempre desde [primary] (Firebase) al
 * caller — comportamiento de producción intacto — y, para los paths marcados en
 * [config], corre [secondary] (server/db-kmp-sdk) en paralelo:
 *
 *  - LECTURAS: el caller recibe únicamente el stream del primario. En background
 *    se observa el secundario y se compara cada snapshot (canónico). Cada
 *    comparación emite un [ShadowReport] vía [onReport] (match o diff).
 *  - ESCRITURAS: se aplican al primario (resultado autoritativo). Si
 *    `mirrorWrites`, se espejan al secundario en best-effort (nunca hacen fallar
 *    la escritura del primario), para poblar el server y que las lecturas sean
 *    comparables.
 *
 * Así se valida la paridad EN VIVO sin riesgo: si algo difiere, el reporte lo
 * marca y nunca se sirvió data del server al usuario.
 */
class ShadowRtdbBackend(
    private val primary: RtdbBackend,
    private val secondary: RtdbBackend,
    private val scope: CoroutineScope,
    private val config: ShadowConfig = ShadowConfig(),
    private val onReport: (ShadowReport) -> Unit = {},
) : RtdbBackend {

    override val name: String = "shadow(${primary.name}->${secondary.name})"

    override fun observeValue(path: String): Flow<JsonElement?> {
        if (!config.isShadowed(path)) return primary.observeValue(path)
        return channelFlow {
            val producer = this
            val mutex = Mutex()
            var p: JsonElement? = null
            var pSet = false
            var s: JsonElement? = null
            var sSet = false

            suspend fun compare() {
                if (pSet && sSet) {
                    onReport(ShadowReport(path, canonicalize(p) == canonicalize(s), p, s))
                }
            }

            val jobPrimary = launch {
                primary.observeValue(path).collect { value ->
                    mutex.withLock { p = value; pSet = true }
                    producer.send(value) // el caller solo ve el primario
                    mutex.withLock { compare() }
                }
            }
            val jobSecondary = launch {
                secondary.observeValue(path).collect { value ->
                    mutex.withLock { s = value; sSet = true; compare() }
                }
            }
            awaitClose {
                jobPrimary.cancel()
                jobSecondary.cancel()
            }
        }
    }

    override suspend fun getValue(path: String): JsonElement? {
        val primaryValue = primary.getValue(path)
        if (config.isShadowed(path)) {
            scope.launch {
                runCatching {
                    val secondaryValue = secondary.getValue(path)
                    onReport(
                        ShadowReport(
                            path,
                            canonicalize(primaryValue) == canonicalize(secondaryValue),
                            primaryValue,
                            secondaryValue,
                        ),
                    )
                }
            }
        }
        return primaryValue
    }

    override suspend fun setValue(path: String, value: JsonElement?) {
        primary.setValue(path, value)
        mirror(path) { secondary.setValue(path, value) }
    }

    override suspend fun updateChildren(path: String, children: Map<String, JsonElement?>) {
        primary.updateChildren(path, children)
        mirror(path) { secondary.updateChildren(path, children) }
    }

    override suspend fun removeValue(path: String) {
        primary.removeValue(path)
        mirror(path) { secondary.removeValue(path) }
    }

    private fun mirror(path: String, block: suspend () -> Unit) {
        if (config.mirrorWrites && config.isShadowed(path)) {
            scope.launch { runCatching { block() } }
        }
    }
}
