package io.github.quwac.repositoryhelper

import kotlinx.coroutines.flow.Flow

interface UserDao {
    fun selectUser(id: Long): Flow<User>
    fun selectUserNullable(id: Long): Flow<User?>
    suspend fun selectUserSuspend(id: Long): User
    suspend fun selectUserNullableSuspend(id: Long): User?

    suspend fun upsertUsers(vararg users: User): Boolean

    suspend fun deleteUsers(vararg ids: Long): Boolean
}