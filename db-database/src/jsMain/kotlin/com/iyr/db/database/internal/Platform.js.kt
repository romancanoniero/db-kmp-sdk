package com.iyr.db.database.internal

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun platformWebSocketClient(): HttpClient =
    HttpClient(Js) {
        install(WebSockets)
    }
