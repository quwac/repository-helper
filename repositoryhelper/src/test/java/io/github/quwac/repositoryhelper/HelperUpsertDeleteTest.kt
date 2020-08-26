package io.github.quwac.repositoryhelper

import android.os.Build
import com.google.common.truth.Truth.assertThat
import io.github.quwac.repositoryhelper.daowrap.CacheDaoWrapper
import io.github.quwac.repositoryhelper.daowrap.ServerDaoWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.O_MR1])
@RunWith(RobolectricTestRunner::class)
class HelperUpsertDeleteTest {
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
    fun upsert_Success_Old() = runBlocking {
        val cache = UserDaoImpl(OLD, 0, "cache")
        val server = UserDaoImpl(null, 0, "server")

        val user = NEW

        val upsertResult = RepositoryHelperImpl.build(
            cacheDaoWrapper = object : CacheDaoWrapper<Long, User, Result<User>> {
                override fun selectFlow(query: Long): Flow<Result<User>> =
                    throw UnsupportedOperationException()

                override suspend fun selectRaw(query: Long): User? =
                    cache.selectUserSuspend(query)

                override suspend fun upsert(entity: User) =
                    cache.upsertUsers(entity).toUnit()

                override suspend fun deleteByQuery(query: Long) =
                    throw UnsupportedOperationException()
            },
            serverDaoWrapper = object : ServerDaoWrapper<Long, User> {
                override suspend fun select(query: Long): User? =
                    throw UnsupportedOperationException()

                override suspend fun upsert(entity: User) =
                    server.upsertUsers(entity).toUnit()

                override suspend fun deleteByQuery(query: Long) =
                    throw UnsupportedOperationException()

            },
            onErrorReturn = { _, _ ->
                throw UnsupportedOperationException()
            }
        ).upsert(user.id, user)

        assertThat(cache.selectUserNullableSuspend(user.id)).isEqualTo(user)
        assertThat(server.selectUserNullableSuspend(user.id)).isEqualTo(user)
    }

    @Test
    fun upsert_Success_Null() = runBlocking {
        val cache = UserDaoImpl(null, 0, "cache")
        val server = UserDaoImpl(null, 0, "server")

        val user = NEW

        val upsertResult = RepositoryHelperImpl.build(
            cacheDaoWrapper = object : CacheDaoWrapper<Long, User, Result<User>> {
                override fun selectFlow(query: Long): Flow<Result<User>> =
                    throw UnsupportedOperationException()

                override suspend fun selectRaw(query: Long): User? =
                    cache.selectUserNullableSuspend(query)

                override suspend fun upsert(entity: User) =
                    cache.upsertUsers(entity).toUnit()

                override suspend fun deleteByQuery(query: Long) =
                    cache.deleteUsers(query).toUnit()
            },
            serverDaoWrapper = object : ServerDaoWrapper<Long, User> {
                override suspend fun select(query: Long): User? =
                    throw UnsupportedOperationException()

                override suspend fun upsert(entity: User) =
                    server.upsertUsers(entity).toUnit()

                override suspend fun deleteByQuery(query: Long) =
                    throw UnsupportedOperationException()

            },
            onErrorReturn = { _, _ ->
                throw UnsupportedOperationException()
            }
        ).upsert(user.id, user)

        assertThat(cache.selectUserNullableSuspend(user.id)).isEqualTo(user)
        assertThat(server.selectUserNullableSuspend(user.id)).isEqualTo(user)
    }

    @Test
    fun upsert_Failure_Old() = runBlocking {
        val cache = UserDaoImpl(OLD, 0, "cache")

        val user = NEW

        val t = RuntimeException()

        val upsertResultFunc = suspend {
            RepositoryHelperImpl.build(
                cacheDaoWrapper = object : CacheDaoWrapper<Long, User, Result<User>> {
                    override fun selectFlow(query: Long): Flow<Result<User>> =
                        throw UnsupportedOperationException()

                    override suspend fun selectRaw(query: Long): User? =
                        cache.selectUserSuspend(query)

                    override suspend fun upsert(entity: User) =
                        cache.upsertUsers(entity).toUnit()

                    override suspend fun deleteByQuery(query: Long) =
                        throw UnsupportedOperationException()
                },
                serverDaoWrapper = object : ServerDaoWrapper<Long, User> {
                    override suspend fun select(query: Long): User? =
                        throw UnsupportedOperationException()


                    override suspend fun upsert(entity: User) {
                        throw t
                    }

                    override suspend fun deleteByQuery(query: Long) =
                        throw UnsupportedOperationException()

                },
                onErrorReturn = { _, _ ->
                    throw UnsupportedOperationException()
                }
            ).upsert(user.id, user)
        }

        val actual = mutableListOf<User?>()
        val job = launch(Dispatchers.IO) {
            cache.selectUserNullable(user.id).toList(actual)
        }
        delay(1000)
        runCatching { upsertResultFunc() }
            .onSuccess {
                assertThat(true).isFalse()
            }
            .onFailure {
                assertThat(it).apply {
                    hasMessageThat().contains("upsert failed")
                    hasCauseThat().isEqualTo(t)
                }
                delay(1000)
                job.cancel()

                assertThat(actual).isEqualTo(listOf(OLD, NEW, OLD))
            }

        Unit
    }

    @Test
    fun upsert_Failure_Null() = runBlocking {
        val cache = UserDaoImpl(null, 0, "cache")

        val user = NEW

        val upsertResultFunc = suspend {
            RepositoryHelperImpl.build(
                cacheDaoWrapper = object : CacheDaoWrapper<Long, User, Result<User>> {
                    override fun selectFlow(query: Long): Flow<Result<User>> =
                        throw UnsupportedOperationException()

                    override suspend fun selectRaw(query: Long): User? =
                        cache.selectUserNullableSuspend(query)

                    override suspend fun upsert(entity: User) =
                        cache.upsertUsers(entity).toUnit()

                    override suspend fun deleteByQuery(query: Long) =
                        cache.deleteUsers(query).toUnit()
                },
                serverDaoWrapper = object : ServerDaoWrapper<Long, User> {
                    override suspend fun select(query: Long): User? =
                        throw UnsupportedOperationException()

                    override suspend fun upsert(entity: User) {
                        throw RuntimeException()
                    }

                    override suspend fun deleteByQuery(query: Long) =
                        throw UnsupportedOperationException()

                },
                onErrorReturn = { _, _ ->
                    throw UnsupportedOperationException()
                }
            ).upsert(user.id, user)
        }

        val actual = mutableListOf<User?>()
        val job = launch(Dispatchers.IO) {
            cache.selectUserNullable(user.id).toList(actual)
        }
        delay(1000)
        runCatching { upsertResultFunc() }
            .onSuccess {
                assertThat(true).isFalse()
            }
            .onFailure {
                assertThat(it).hasMessageThat().contains("upsert failed")

                delay(1000)
                job.cancel()

                assertThat(actual).isEqualTo(listOf(null, NEW, null))
            }

        Unit
    }

    @Test
    fun delete_Success_Old() = runBlocking {
        val cache = UserDaoImpl(OLD, 0, "cache")
        val server = UserDaoImpl(OLD, 0, "server")

        val deleteResultFunc = suspend {
            RepositoryHelperImpl.build(
                cacheDaoWrapper = object : CacheDaoWrapper<Long, User, Result<User>> {
                    override fun selectFlow(query: Long): Flow<Result<User>> =
                        throw UnsupportedOperationException()

                    override suspend fun selectRaw(query: Long): User? =
                        cache.selectUserNullableSuspend(query)

                    override suspend fun upsert(entity: User) =
                        throw UnsupportedOperationException()

                    override suspend fun deleteByQuery(query: Long) =
                        cache.deleteUsers(query).toUnit()
                },
                serverDaoWrapper = object : ServerDaoWrapper<Long, User> {
                    override suspend fun select(query: Long): User? =
                        throw UnsupportedOperationException()

                    override suspend fun upsert(entity: User) =
                        server.upsertUsers(entity).toUnit()

                    override suspend fun deleteByQuery(query: Long) =
                        server.deleteUsers(query).toUnit()

                },
                onErrorReturn = { _, _ ->
                    throw UnsupportedOperationException()
                }
            ).delete(OLD.id)
        }

        var result = true
        val job = launch {
            try {
                deleteResultFunc()
            } catch (t: Throwable) {
                result = false
            }
        }
        delay(1000)
        job.cancel()

        outputLog()
        assertThat(result).isEqualTo(true)
        assertThat(cache.selectUserNullableSuspend(OLD.id)).isEqualTo(null)
        assertThat(server.selectUserNullableSuspend(OLD.id)).isEqualTo(null)
    }

    @Test
    fun delete_Success_Null() = runBlocking {
        val cache = UserDaoImpl(null, 0, "cache")
        val server = UserDaoImpl(null, 0, "server")

        val deleteResultFunc = suspend {
            RepositoryHelperImpl.build(
                cacheDaoWrapper = object : CacheDaoWrapper<Long, User, Result<User>> {
                    override fun selectFlow(query: Long): Flow<Result<User>> =
                        throw UnsupportedOperationException()

                    override suspend fun selectRaw(query: Long): User? =
                        cache.selectUserNullableSuspend(query)

                    override suspend fun upsert(entity: User) =
                        throw UnsupportedOperationException()

                    override suspend fun deleteByQuery(query: Long) =
                        cache.deleteUsers(query).toUnit()
                },
                serverDaoWrapper = object : ServerDaoWrapper<Long, User> {
                    override suspend fun select(query: Long): User? =
                        throw UnsupportedOperationException()

                    override suspend fun upsert(entity: User) =
                        throw UnsupportedOperationException()

                    override suspend fun deleteByQuery(query: Long) =
                        server.deleteUsers(query).toUnit()

                },
                onErrorReturn = { _, _ ->
                    throw UnsupportedOperationException()
                }
            ).delete(OLD.id)
        }

        var result = true
        val job = launch {
            try {
                deleteResultFunc()
            } catch (t: Throwable) {
                result = false
            }
        }
        delay(1000)
        job.cancel()

        outputLog()
        assertThat(result).isEqualTo(true)
        assertThat(cache.selectUserNullableSuspend(OLD.id)).isEqualTo(null)
        assertThat(server.selectUserNullableSuspend(OLD.id)).isEqualTo(null)
    }

    @Test
    fun delete_Failure_Old() = runBlocking {
        val cache = UserDaoImpl(OLD, 0, "cache")

        val t = RuntimeException()

        val deleteResultFunc = suspend {
            RepositoryHelperImpl.build(
                cacheDaoWrapper = object : CacheDaoWrapper<Long, User, Result<User>> {
                    override fun selectFlow(query: Long): Flow<Result<User>> =
                        throw UnsupportedOperationException()

                    override suspend fun selectRaw(query: Long): User? =
                        cache.selectUserNullableSuspend(query)

                    override suspend fun upsert(entity: User) =
                        cache.upsertUsers(entity).toUnit()

                    override suspend fun deleteByQuery(query: Long) =
                        cache.deleteUsers(query).toUnit()
                },
                serverDaoWrapper = object : ServerDaoWrapper<Long, User> {
                    override suspend fun select(query: Long): User? =
                        throw UnsupportedOperationException()

                    override suspend fun upsert(entity: User) =
                        throw UnsupportedOperationException()

                    override suspend fun deleteByQuery(query: Long) =
                        throw t

                },
                onErrorReturn = { _, _ ->
                    throw UnsupportedOperationException()
                }
            ).delete(OLD.id)
        }

        val actual = mutableListOf<User?>()
        val job = launch(Dispatchers.IO) {
            cache.selectUserNullable(OLD.id).toList(actual)
        }
        delay(1000)

        runCatching {
            deleteResultFunc()
        }.onSuccess { assertThat(true).isFalse() }
            .onFailure {
                assertThat(it).apply {
                    hasCauseThat().isEqualTo(t)
                    hasMessageThat().contains("delete failed. query=")
                }

                delay(1000)
                job.cancel()
                assertThat(actual).isEqualTo(listOf(OLD, null, OLD))
            }

        Unit
    }

    @Test
    fun delete_Failure_Null() = runBlocking {
        val cache = UserDaoImpl(null, 0, "cache")

        val t = RuntimeException()

        val deleteResultFunc = suspend {
            RepositoryHelperImpl.build(
                cacheDaoWrapper = object : CacheDaoWrapper<Long, User, Result<User>> {
                    override fun selectFlow(query: Long): Flow<Result<User>> =
                        throw UnsupportedOperationException()

                    override suspend fun selectRaw(query: Long): User? =
                        cache.selectUserNullableSuspend(query)

                    override suspend fun upsert(entity: User) =
                        cache.upsertUsers(entity).toUnit()

                    override suspend fun deleteByQuery(query: Long) =
                        cache.deleteUsers(query).toUnit()
                },
                serverDaoWrapper = object : ServerDaoWrapper<Long, User> {
                    override suspend fun select(query: Long): User? =
                        throw UnsupportedOperationException()

                    override suspend fun upsert(entity: User) =
                        throw UnsupportedOperationException()

                    override suspend fun deleteByQuery(query: Long) =
                        throw t

                },
                onErrorReturn = { _, _ ->
                    throw UnsupportedOperationException()
                }
            ).delete(OLD.id)
        }

        val actual = mutableListOf<User?>()
        val job = launch(Dispatchers.IO) {
            cache.selectUserNullable(OLD.id).toList(actual)
        }
        delay(1000)

        runCatching {
            deleteResultFunc()
        }.onSuccess { assertThat(true).isFalse() }
            .onFailure {
                assertThat(it).apply {
                    hasCauseThat().isEqualTo(t)
                    hasMessageThat().contains("delete failed. query=")
                }

                delay(1000)
                job.cancel()
                assertThat(actual).isEqualTo(listOf(null, null))
            }

        Unit
    }
}