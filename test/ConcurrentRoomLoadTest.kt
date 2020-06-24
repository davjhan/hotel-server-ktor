package com.davjhan

import RoomState
import hotel.ClosureCode
import hotel.Lobby
import hotel.User
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.Math.random

class ConcurrentRoomLoadTest {
    @Test
    fun `concurrent Lobby test`() {
        runBlocking {
            val lobby = Lobby(RoomState.serializer(), mutableMapOf()) { mockk(relaxed = true) }
            runBlocking {
                repeat(1000) {
                    launch {
                        val roomId = lobby.createRoom()
                        delay((random()*1000).toLong())
                        lobby.removeRoom(roomId)
                    }
                }
            }
            assertEquals(lobby.rooms.size, 0)
        }
    }

    @Test
    fun `concurrent CreateRoom test`() {
        runBlocking {
            val lobby = Lobby(RoomState.serializer(), mutableMapOf()) { mockk(relaxed = true) }
            runBlocking {
                repeat(100) {
                    launch {
                        val roomId = lobby.createRoom()
                        repeat(10) {
                            launch {
                                delay((random()*1000).toLong())
                                lobby.addUserToRoom(User(it.toString(), "user-$it", mockk(relaxed = true)), roomId)
                            }
                        }
                    }
                }
            }
            assertEquals(lobby.rooms.size, 100)
            lobby.rooms.values.forEach {
                assertEquals(it.currentUsers.size, 10)
                it.kickAll(ClosureCode.RoomDeleted)
                assertEquals(it.currentUsers.size, 0)
            }
        }
    }
}
