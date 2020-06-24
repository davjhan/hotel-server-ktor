package hotel

import Room
import RoomState
import kotlinx.serialization.Serializable

interface RoomLogic<S : RoomState, M> {
    fun onMessage(room: Room<S>, playerName: String, message: M)
}

@Serializable
data class ErrorMessage(val message: String) {
    constructor(e: Throwable) : this("Error: ${e.message}")
}

/**
 * Exception to be thrown when processing an Invalid message.
 */
class InvalidMessageException(override val message: String) : RuntimeException()