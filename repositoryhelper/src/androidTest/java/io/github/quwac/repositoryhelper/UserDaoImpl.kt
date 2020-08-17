@file:Suppress("EXPERIMENTAL_API_USAGE")

package io.github.quwac.repositoryhelper

import android.util.Log
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapNotNull

class UserDaoImpl(
    initialUser: User?,
    private val delay: Long,
    private val name: String
) : UserDao {
    private val userChannel = ConflatedBroadcastChannel(initialUser)

    private val userFlow = userChannel.asFlow()

    private var user: User? = initialUser

    override fun selectUser(id: Long): Flow<User> = userFlow.mapNotNull { it }.let {
        if (delay > 0) {
            it.debounce(delay)
        } else {
            it
        }
    }

    override fun selectUserNullable(id: Long): Flow<User?> = userFlow.let {
        if (delay > 0) {
            it.debounce(delay)
        } else {
            it
        }
    }

    override suspend fun selectUserSuspend(id: Long): User = user!!
    override suspend fun selectUserNullableSuspend(id: Long): User? = user

    override suspend fun upsertUsers(vararg users: User): Boolean {
        log("upsertUsers", "name=${name},users=${users.toList()}")
        val u = users[0]
        user = u
        try {
            userChannel.send(u)
        } catch (t: Throwable) {
            Log.e("upsertUsers", "error", t)
        }
        log("upsertUsers", "OK: name=$name,u=$u")

        return true
    }

    override suspend fun deleteUsers(vararg ids: Long): Boolean {
        userChannel.send(null)
        user = null
        log("deleteUsers", "name=${name} deleted.")
        return true
    }
}