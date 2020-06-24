import hotel.ClosureCode
import hotel.User
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.time.Instant

/**
 * Override this to implement your room's state.
 */
@Serializable
abstract class RoomState {
    abstract fun onUserAdded(user: User)
    abstract fun onUserRemoved(user: User, currentUsers: List<User>, wasKicked: Boolean = false)
}

val json = Json(JsonConfiguration.Stable)

data class Room<S : RoomState>(
    val roomId: String,
    var state: S,
    private val stateSerializer: KSerializer<S>,
    private val _currentUsers: MutableList<User> = mutableListOf(),
    private var _lastUpdated: Instant = Instant.now()
) {
    val currentUsers: List<User> get() = _currentUsers
    val lastUpdated: Instant get() = _lastUpdated

    private val mutex = Mutex()

    suspend fun hasUserWithName(name: String): Boolean = mutex.withLock {
        return _currentUsers.any { it.name == name }
    }

    internal suspend fun addUser(user: User) {
        mutex.withLock {
            if (_currentUsers.any { it.session == user.session }) return
            state.onUserAdded(user)
            _currentUsers.add(user)
            _lastUpdated = Instant.now()
        }
        broadcastState()
    }

    suspend fun removeUserWithName(name: String) = removeUser { it.name == name }
    suspend fun removeUserWithConnectionId(connectionId: String) = removeUser { it.connectionId == connectionId }
    private suspend fun removeUser(predicate: (User) -> Boolean) {
        mutex.withLock {
            _currentUsers.find(predicate)?.let { user ->
                _currentUsers.remove(user)
                state.onUserRemoved(user, _currentUsers)
            }
            _lastUpdated = Instant.now()

        }
        broadcastState()
    }

    suspend fun <R> withLock(action: suspend Room<S>.() -> R) = mutex.withLock { action() }
    override fun toString(): String {
        return "[${roomId}](${currentUsers.size} ppl): $state"
    }

    suspend fun broadcastState(blacklist: List<User> = emptyList()) = mutex.withLock {
        (if (blacklist.isEmpty()) _currentUsers else _currentUsers).filter { !blacklist.contains(it) }
            .forEach {
                kotlin.runCatching { it.session.send(Frame.Text(json.stringify(stateSerializer, state))) }
            }
    }

    suspend fun kickAll(closureCode: ClosureCode) = mutex.withLock {
        _currentUsers.forEach { it.session.close(CloseReason(closureCode.code, closureCode.message)) }
        _currentUsers.clear()
    }

    fun shortToString(): String = "[${roomId}](${currentUsers.size} ppl)"
}