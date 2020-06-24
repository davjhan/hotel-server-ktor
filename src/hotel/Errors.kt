package hotel

import kotlinx.serialization.Serializable



enum class ClosureCode(val code: Short, val message: String) {
    RoomNotFound(2001, "Room not found"),
    NameInUse(2002, "Name in use"),
    InvalidName(2003, "Invalid name"),
    RoomDeleted(2004, "Room deleted"),
}

/**
 * Exception to be thrown when web socket needs to be closed.
 */
class DisconnectException(val closureCode: ClosureCode) : RuntimeException()
