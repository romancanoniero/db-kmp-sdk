package com.iyr.db.database.internal

import io.ktor.client.HttpClient

/**
 * Único punto expect/actual del SDK: el engine de Ktor por plataforma.
 * Android -> OkHttp, iOS -> Darwin, JS -> Js (WebSocket del navegador).
 *
 * Toda la lógica de protocolo/listeners/cache vive en commonMain.
 */
internal expect fun platformWebSocketClient(): HttpClient
