package com.iyr.db.database.internal

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun platformWebSocketClient(): HttpClient =
    HttpClient(Darwin) {
        install(WebSockets)
    }
