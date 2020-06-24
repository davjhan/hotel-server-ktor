package com.davjhan

import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlin.test.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration

class ApplicationTest {
    val json = Json(JsonConfiguration.Default)

    @Test
    fun testRoot() {
        withTestApplication({ module(testing = true) }) {
            var roomCode: String
            handleRequest(HttpMethod.Post, "/createRoom").apply {
                assertEquals(HttpStatusCode.OK, response.status())
                roomCode = json.parseJson(response.content!!).jsonObject["roomId"]!!.primitive.content
                assertTrue { roomCode.isNotEmpty() }
            }
            println("[TEST] create room Response: ${roomCode}")
            handleWebSocketConversation("/room/user1/$roomCode") { incoming1, outgoing1 ->
                // We then receive two messages (the message notifying that the member joined, and the message we sent echoed to us)

                handleWebSocketConversation("/room/user2/$roomCode") { incoming2, outgoing2 ->
                    // Send a HELLO message
                    outgoing1.send(Frame.Text("user1 says hi"))
                    println("[TEST 1] ${(incoming1.receive() as Frame.Text).readText()}")
                    outgoing2.send(Frame.Text("user2 says hi"))
                    println("[TEST 1] ${(incoming1.receive() as Frame.Text).readText()}")
                    println("[TEST 2] ${(incoming2.receive() as Frame.Text).readText()}")
                }
            }
        }
    }
}
