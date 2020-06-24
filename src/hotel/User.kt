package hotel

import io.ktor.http.cio.websocket.WebSocketSession

data class User(
    val connectionId: String,
    val name: String,
    val session: WebSocketSession
)