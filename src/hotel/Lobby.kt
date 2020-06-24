package hotel

import Room
import RoomState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer

data class Lobby<S : RoomState>(
    val roomStateSerializer: KSerializer<S>,
    val rooms: MutableMap<String, Room<S>> = mutableMapOf(),
    val newRoomState: (roomId: String) -> S
) {
    private val mutex = Mutex()

    /**
     * Creates a room.
     */
    suspend fun createRoom(): String = mutex.withLock {
        val roomId: String = newRoomId()
        rooms[roomId] = Room(roomId, newRoomState(roomId), stateSerializer = roomStateSerializer)
        return roomId
    }

    /**
     * Add a user to Room.
     * @throws DisconnectException with RoomNotFound if roomId is invalid.
     * @throws DisconnectException with NameInUse if name is already taken.
     */
    suspend fun addUserToRoom(user: User, roomId: String): Room<S> = mutex.withLock {
        val room = rooms[roomId] ?: throw DisconnectException(ClosureCode.RoomNotFound)
        if (room.hasUserWithName(user.name)) {
            throw DisconnectException(ClosureCode.NameInUse)
        }
        room.addUser(user)
        room
    }

    /**
     * Removes user with the given connectionId.
     */
    suspend fun removeUserFromRoom(connectionId: String, roomId: String) = mutex.withLock {
        rooms[roomId]?.removeUserWithConnectionId(connectionId)
    }


    /**
     * Cleans up all rooms matching the given predicate
     */
    suspend fun removeRooms(shouldRemove: (Room<S>) -> Boolean):List<Room<S>> = mutex.withLock {
        val toRemove = rooms.values.filter(shouldRemove)
        toRemove.forEach { removeRoom(it) }
        return toRemove
    }

    /**
     * Removes a room.
     */
    suspend fun removeRoom(roomId: String) = rooms[roomId]?.let { removeRoom(it) }

    /**
     * Removes a room.
     */
    suspend fun removeRoom(room: Room<S>) = mutex.withLock {
        room.kickAll(ClosureCode.RoomDeleted)
        rooms.remove(room.roomId)
    }

    /**
     * Expects to be called while mutex is locked.
     */
    private suspend fun newRoomId(): String {
        val roomId = (1..6).map { STRING_CHARACTERS.random() }.joinToString("")
        return if (rooms.contains(roomId)) newRoomId()
        else roomId
    }
}

private val STRING_CHARACTERS = ('A'..'Z').toList().toTypedArray()


