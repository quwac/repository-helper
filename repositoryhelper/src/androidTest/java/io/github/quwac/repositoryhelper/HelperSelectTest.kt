@file:Suppress("EXPERIMENTAL_API_USAGE")

package io.github.quwac.repositoryhelper

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test

class HelperSelectTest {
    companion object {
        private val OLD = User(0, "old")
        private val NEW = User(0, "new")
    }

    @Before
    fun setup() {
        RhLog.isRepositoryHelperLogEnable = true
    }

    @After
    fun tearDown() {
        RhLog.isRepositoryHelperLogEnable = false
    }

    @Test
    fun selectUserTest_Loading_Old_New() = runBlocking {
        val cache = UserDaoImpl(OLD, 100, "cache")
        val server = UserDaoImpl(NEW, 0, "server")

        val user = RepositoryHelper.build(
            cacheDao = object : DefaultCacheDaoWrapper<Long, User, Result<User>> {
                override fun selectFlow(query: Long): Flow<Result<User>> =
                    cache.selectUser(query).toResult()

                override suspend fun selectRaw(query: Long): User? =
                    throw UnsupportedOperationException()

                override suspend fun upsert(entity: User) = cache.upsertUsers(entity)

                override suspend fun delete(query: Long) = cache.deleteUsers(query)

            },
            serverDao = object : DefaultServerDaoWrapper<Long, User> {
                override suspend fun select(query: Long): User? {
                    delay(1000)
                    return server.selectUserSuspend(query)
                }

                override suspend fun upsert(entity: User) {
                    throw UnsupportedOperationException()
                }

                override suspend fun delete(query: Long) {
                    throw UnsupportedOperationException()
                }
            },
            onErrorReturn = { result, e ->
                onErrorResultReturn(result, e)
            }
        ).select(0)

        val actual = mutableListOf<Result<User?>>()
        val job = launch(Dispatchers.IO) {
            user.toList(actual)
        }

        val expected = listOf(
            Result.loading<User>(),
            Result.Success(OLD),
            Result.Success(NEW)
        )

        withContext(Dispatchers.IO) {
            delay(2000)
        }
        job.cancel()
        outputLog()
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun selectUserTest_Loading_Old_NotFound() = runBlocking {
        val cache = UserDaoImpl(OLD, 100, "cache")
        val server = UserDaoImpl(null, 0, "server")

        val user = RepositoryHelper.build(
            cacheDao = object : DefaultCacheDaoWrapper<Long, User, Result<User?>> {
                override fun selectFlow(query: Long): Flow<Result<User?>> =
                    cache.selectUserNullable(query).toResult()

                override suspend fun selectRaw(query: Long): User? =
                    throw UnsupportedOperationException()

                override suspend fun upsert(entity: User) = cache.upsertUsers(entity)

                override suspend fun delete(query: Long) = cache.deleteUsers(query)

            },
            serverDao = object : DefaultServerDaoWrapper<Long, User> {
                override suspend fun select(query: Long): User? {
                    delay(1000)
                    return server.selectUserNullableSuspend(query)
                }

                override suspend fun upsert(entity: User) {
                    throw UnsupportedOperationException()
                }

                override suspend fun delete(query: Long) {
                    throw UnsupportedOperationException()
                }
            },
            onErrorReturn = { result, e ->
                onErrorResultReturn(result, e)
            }
        ).select(0)

        val actual = mutableListOf<Result<User?>>()
        val job = launch(Dispatchers.IO) {
            user.toList(actual)
        }

        val expected = listOf(
            Result.loading<User>(),
            Result.Success(OLD),
            Result.notFound()
        )

        withContext(Dispatchers.IO) {
            delay(2000)
        }
        job.cancel()
        outputLog()
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun selectUserTest_Loading_NotFound_New() = runBlocking {
        val cache = UserDaoImpl(null, 100, "cache")
        val server = UserDaoImpl(NEW, 0, "server")

        val user = RepositoryHelper.build(
            cacheDao = object : DefaultCacheDaoWrapper<Long, User, Result<User?>> {
                override fun selectFlow(query: Long): Flow<Result<User?>> =
                    cache.selectUserNullable(query).toResult()

                override suspend fun selectRaw(query: Long): User? =
                    throw UnsupportedOperationException()

                override suspend fun upsert(entity: User) = cache.upsertUsers(entity)

                override suspend fun delete(query: Long) = cache.deleteUsers(query)

            },
            serverDao = object : DefaultServerDaoWrapper<Long, User> {
                override suspend fun select(query: Long): User? {
                    delay(1000)
                    return server.selectUserSuspend(query)
                }

                override suspend fun upsert(entity: User) {
                    throw UnsupportedOperationException()
                }

                override suspend fun delete(query: Long) {
                    throw UnsupportedOperationException()
                }
            },
            onErrorReturn = { result, e ->
                onErrorResultReturn(result, e)
            }
        ).select(0)

        val actual = mutableListOf<Result<User?>>()
        val job = launch(Dispatchers.IO) {
            user.toList(actual)
        }

        val expected = listOf(
            Result.loading<User>(),
            Result.notFound(),
            Result.Success(NEW)
        )

        withContext(Dispatchers.IO) {
            delay(2000)
        }
        job.cancel()
        outputLog()
        assertThat(actual).isEqualTo(expected)
    }


    @Test
    fun selectUserTest_Loading_NotFound_NotFound() = runBlocking {
        val cache = UserDaoImpl(null, 100, "cache")
        val server = UserDaoImpl(null, 0, "server")

        val user = RepositoryHelper.build(
            cacheDao = object : DefaultCacheDaoWrapper<Long, User, Result<User?>> {
                override fun selectFlow(query: Long): Flow<Result<User?>> =
                    cache.selectUserNullable(query).toResult()

                override suspend fun selectRaw(query: Long): User? =
                    throw UnsupportedOperationException()

                override suspend fun upsert(entity: User) = cache.upsertUsers(entity)

                override suspend fun delete(query: Long) = cache.deleteUsers(query)

            },
            serverDao = object : DefaultServerDaoWrapper<Long, User> {
                override suspend fun select(query: Long): User? {
                    delay(1000)
                    return server.selectUserNullableSuspend(query)
                }

                override suspend fun upsert(entity: User) {
                    throw UnsupportedOperationException()
                }

                override suspend fun delete(query: Long) {
                    throw UnsupportedOperationException()
                }
            },
            onErrorReturn = { result, e ->
                onErrorResultReturn(result, e)
            }
        ).select(0)

        val actual = mutableListOf<Result<User?>>()
        val job = launch(Dispatchers.IO) {
            user.toList(actual)
        }

        val expected = listOf(
            Result.loading<User>(),
            Result.notFound<User>(),
            Result.notFound()
        )

        withContext(Dispatchers.IO) {
            delay(2000)
        }
        job.cancel()
        outputLog()
        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun selectUserTest_Loading_NotFound_Error() = runBlocking {
        val cache = UserDaoImpl(null, 100, "cache")
        val t = RuntimeException()

        val user = RepositoryHelper.build(
            cacheDao = object : DefaultCacheDaoWrapper<Long, User, Result<User?>> {
                override fun selectFlow(query: Long): Flow<Result<User?>> =
                    cache.selectUserNullable(query).toResult()

                override suspend fun selectRaw(query: Long): User? =
                    throw UnsupportedOperationException()

                override suspend fun upsert(entity: User) = cache.upsertUsers(entity)

                override suspend fun delete(query: Long) = cache.deleteUsers(query)

            },
            serverDao = object : DefaultServerDaoWrapper<Long, User> {
                override suspend fun select(query: Long): User? {
                    delay(1000)
                    throw t
                }

                override suspend fun upsert(entity: User) {
                    throw UnsupportedOperationException()
                }

                override suspend fun delete(query: Long) {
                    throw UnsupportedOperationException()
                }
            },
            onErrorReturn = { result, e ->
                onErrorResultReturn(result, e)
            }
        ).select(0)

        val actual = mutableListOf<Result<User?>>()
        val job = launch(Dispatchers.IO) {
            user.toList(actual)
        }

        val expected = listOf(
            Result.loading<User>(),
            Result.notFound<User>(),
            Result.Error(t)
        )

        withContext(Dispatchers.IO) {
            delay(2000)
        }
        job.cancel()
        outputLog()
        assertThat(actual).isEqualTo(expected)
    }
}

