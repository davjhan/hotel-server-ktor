package hotel

import RoomState
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.features.CORS
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.http.cio.websocket.*
import io.ktor.response.respond
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.serialization.json
import io.ktor.websocket.webSocket
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.time.Duration
import java.util.*
import java.util.concurrent.CancellationException


val json = Json(JsonConfiguration.Stable)

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun <S : RoomState, M> Application.hotelModule(
    testing: Boolean = false,
    lobby: Lobby<S>,
    messageSerializer: KSerializer<M>,
    roomLogic: RoomLogic<S, M>,
    pathPrefix: String = "",
    cleanupWorkerFrequency: Duration = Duration.ofMinutes(15)
) {
    install(CallLogging)
    install(CORS) {
        anyHost()
    }
    install(ContentNegotiation) {
        json()
    }
    install(io.ktor.websocket.WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    routing {
        post("$pathPrefix/createRoom") {
            val roomId = lobby.createRoom()
            call.respond(mapOf("roomId" to roomId))
            log.info("✔︎ Created Room $roomId")
        }
        post("$pathPrefix/listRooms") {
            val response = lobby.rooms.values.map {
                mapOf(
                    "roomId" to it.roomId,
                    "currentUsers" to it.currentUsers.map { it.name }
                )
            }
            call.respond(response)
            log.info("✔︎ Listed ${response.size} rooms.")
        }

        webSocket("$pathPrefix/room/{name}/{roomId}") {
            val connectionId = UUID.randomUUID().toString()
            val name = call.parameters["name"] ?: "__missing__"
            val roomId = call.parameters["roomId"] ?: "__missing__"
            log.info("Incoming connection: $connectionId with name $name joined room $roomId")
            try {
                if (name.isEmpty()) closeWithCode(ClosureCode.InvalidName)
                val user = User(connectionId, name, this)
                val room = kotlin.runCatching { lobby.addUserToRoom(user, roomId) }
                    .getOrElse {
                        when (it) {
                            is DisconnectException -> closeWithCode(it.closureCode)
                            else -> close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Unexpected Close"))
                        }
                        return@webSocket
                    }
                log.info("✔︎ $name joined room ${room.shortToString()}.  (id: $connectionId)")
                while (true) {
                    val frame = incoming.receive()
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        runCatching { json.parse(messageSerializer, text) }
                            .onFailure {
                                log.warn("✘︎ Could not parse message: $text")
                                sendError("Could not parse Message")
                            }
                            .onSuccess { message ->
                                kotlin.runCatching {
                                    roomLogic.onMessage(
                                        room,
                                        playerName = user.name,
                                        message = message
                                    )
                                }.onFailure {
                                    sendError(it)
                                    log.info("✘ Invalid Message: $message in $room")
                                }.onSuccess {
                                    room.broadcastState()
                                    log.info("✔︎ Processed Message: $message in $room")
                                }
                            }
                    }
                }
            } catch (e: Exception) {
                when (e) {
                    is ClosedReceiveChannelException -> log.info("✔︎ Disconnected user: $name in room $roomId")
                    is CancellationException -> Unit
                    else -> {
                        log.error("Exception! $e")
                        e.printStackTrace()
                    }
                }

            } finally {
                lobby.removeUserFromRoom(connectionId, roomId)
            }

        }
    }

    launch {
        while (true) {
            delay(cleanupWorkerFrequency.toMillis())
            val removedRooms = lobby.removeRooms { it.currentUsers.isEmpty() }
            log.info("✔︎ Cleanup Worker removed ${removedRooms.size} rooms.")
        }
    }
}

private suspend fun WebSocketSession.sendError(throwable: Throwable) =
    send(
        Frame.Text(
            json.stringify(
                ErrorMessage.serializer(),
                ErrorMessage(throwable)
            )
        )
    )

private suspend fun WebSocketSession.sendError(message: String) =
    send(
        Frame.Text(
            json.stringify(
                ErrorMessage.serializer(),
                ErrorMessage(message)
            )
        )
    )

private suspend fun WebSocketSession.closeWithCode(closureCode: ClosureCode) =
    close(CloseReason(closureCode.code, closureCode.message))
