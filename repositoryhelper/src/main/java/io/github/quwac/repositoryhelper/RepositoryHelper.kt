package io.github.quwac.repositoryhelper

import io.github.quwac.repositoryhelper.daowrap.CacheDaoWrapper
import io.github.quwac.repositoryhelper.daowrap.NoQueryCacheDaoWrapper
import io.github.quwac.repositoryhelper.daowrap.NoQuerySelectOnlyServerDaoWrapper
import io.github.quwac.repositoryhelper.daowrap.SelectOnlyServerDaoWrapper
import io.github.quwac.repositoryhelper.daowrap.ServerDaoWrapper
import io.github.quwac.repositoryhelper.daowrap.toCacheDaoWrapper
import io.github.quwac.repositoryhelper.daowrap.toServerDaoWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

@Suppress("EXPERIMENTAL_API_USAGE")
class RepositoryHelper<QUERY, ENTITY, READ_RESULT> private constructor(
    private val cacheDaoWrapper: CacheDaoWrapper<QUERY, ENTITY, READ_RESULT>,
    private val serverDaoWrapper: ServerDaoWrapper<QUERY, ENTITY>,
    private val onErrorReturn: (READ_RESULT, Throwable) -> READ_RESULT,
    private val coroutineContext: CoroutineContext,
    private val remoteAccessManager: RemoteAccessManager,
    private val selectQueryToHash: QueryToHash<QUERY>
) {
    companion object {
        fun <QUERY, ENTITY, READ_RESULT> builder(
            cacheDaoWrapper: CacheDaoWrapper<QUERY, ENTITY, READ_RESULT>,
            serverDaoWrapper: ServerDaoWrapper<QUERY, ENTITY>,
            onErrorReturn: (READ_RESULT, Throwable) -> READ_RESULT
        ) = Builder(
            cacheDaoWrapper = cacheDaoWrapper,
            serverDaoWrapper = serverDaoWrapper,
            onErrorReturn = onErrorReturn
        )

        fun <ENTITY, READ_RESULT> builder(
            noQueryCacheDaoWrapper: NoQueryCacheDaoWrapper<ENTITY, READ_RESULT>,
            noQuerySelectOnlyServerDaoWrapper: NoQuerySelectOnlyServerDaoWrapper<ENTITY>,
            onErrorReturn: (READ_RESULT, Throwable) -> READ_RESULT
        ) = Builder(
            cacheDaoWrapper = noQueryCacheDaoWrapper.toCacheDaoWrapper(),
            serverDaoWrapper = noQuerySelectOnlyServerDaoWrapper.toServerDaoWrapper(),
            onErrorReturn = onErrorReturn
        )

        fun <QUERY, ENTITY, READ_RESULT> builder(
            cacheDaoWrapper: CacheDaoWrapper<QUERY, ENTITY, READ_RESULT>,
            selectOnlyServerDaoWrapper: SelectOnlyServerDaoWrapper<QUERY, ENTITY>,
            onErrorReturn: (READ_RESULT, Throwable) -> READ_RESULT
        ) = Builder(
            cacheDaoWrapper = cacheDaoWrapper,
            serverDaoWrapper = selectOnlyServerDaoWrapper.toServerDaoWrapper(),
            onErrorReturn = onErrorReturn
        )
    }

    class Builder<QUERY, ENTITY, READ_RESULT> internal constructor(
        private val cacheDaoWrapper: CacheDaoWrapper<QUERY, ENTITY, READ_RESULT>,
        private val serverDaoWrapper: ServerDaoWrapper<QUERY, ENTITY>,
        private val onErrorReturn: (READ_RESULT, Throwable) -> READ_RESULT
    ) {
        var coroutineContext: CoroutineContext = GlobalScope.coroutineContext + Dispatchers.IO
        var remoteAccessManager: RemoteAccessManager = RemoteAccessManager(
            3000
        )
        var selectQueryToHash: QueryToHash<QUERY> = { it.toString() }

        fun build(): RepositoryHelper<QUERY, ENTITY, READ_RESULT> {
            return RepositoryHelper(
                cacheDaoWrapper = cacheDaoWrapper,
                serverDaoWrapper = serverDaoWrapper,
                onErrorReturn = onErrorReturn,
                coroutineContext = coroutineContext,
                remoteAccessManager = remoteAccessManager,
                selectQueryToHash = selectQueryToHash
            )
        }
    }

    private val mutexManager = MutexManager(selectQueryToHash)

    fun select(query: QUERY): Flow<READ_RESULT> {
        val cacheFlow = cacheDaoWrapper.selectFlow(query)

        val queryHash = selectQueryToHash(query)
        val requestError = accessServer(query, queryHash)

        return mergeCacheAndRemote(cacheFlow, requestError)
    }

    private fun accessServer(
        query: QUERY,
        hash: String
    ): Flow<Throwable?> {
        return channelFlow<Throwable?> {
            send(null)

            val canAccessNow = remoteAccessManager.canAccessNow(hash).also {
                RhLog.d { "accessServer: canAccessNow=$it" }
            }
            if (canAccessNow) {
                runCatching {
                    val entity = serverDaoWrapper.select(query).also {
                        RhLog.d { "accessServer: entity=$it" }
                    }

                    if (entity != null) {
                        cacheDaoWrapper.upsert(entity)
                    } else {
                        cacheDaoWrapper.deleteByQuery(query)
                    }
                    remoteAccessManager.logAccess(hash)
                }.onFailure { t ->
                    RhLog.d { "accessServer: onFailure. t=$t" }
                    send(t)
                }
            }
        }.flowOn(coroutineContext)
    }

    private fun mergeCacheAndRemote(
        cacheFlow: Flow<READ_RESULT>,
        requestError: Flow<Throwable?>
    ): Flow<READ_RESULT> {
        return cacheFlow.combine(requestError) { cacheValue, requestErrorValue ->
            RhLog.d { "mergeCacheAndRemote combine start. cacheValue=$cacheValue, requestError=$requestErrorValue" }
            if (requestErrorValue != null) {
                onErrorReturn(cacheValue, requestErrorValue)
            } else {
                cacheValue
            }.also {
                RhLog.d { "mergeCacheAndRemote combine end. result=$it" }
            }
        }
    }

    suspend fun refresh(refreshTargetQuery: QUERY) {
        try {
            val newValue = serverDaoWrapper.select(refreshTargetQuery)
            if (newValue != null) {
                cacheDaoWrapper.upsert(newValue)
            } else {
                cacheDaoWrapper.deleteByQuery(refreshTargetQuery)
            }
        } catch (t: Throwable) {
            throw RepositoryHelperException(
                "refresh failed. refreshTargetQuery=$refreshTargetQuery",
                t
            )
        }
    }

    suspend fun upsert(query: QUERY, newValue: ENTITY) {
        val mutex = mutexManager.get(query)
        mutex.withLock {
            val backup = cacheDaoWrapper.selectRaw(query)

            val result = cacheDaoWrapper.upsert(newValue)

            return try {
                serverDaoWrapper.upsert(newValue)
                result
            } catch (t: Throwable) {
                RhLog.d { "upsert: backup=$backup, t=$t" }
                if (backup != null) {
                    cacheDaoWrapper.upsert(backup)
                } else {
                    cacheDaoWrapper.deleteByQuery(query)
                }
                throw RepositoryHelperException("upsert failed. query=$query,newValue=$newValue", t)
            }
        }
    }

    suspend fun delete(
        query: QUERY
    ) {
        val mutex = mutexManager.get(query)
        mutex.withLock {
            val backup = cacheDaoWrapper.selectRaw(query)

            val result = cacheDaoWrapper.deleteByQuery(query)

            return try {
                serverDaoWrapper.deleteByQuery(query)
                result
            } catch (t: Throwable) {
                RhLog.d { "delete: backup=$backup, t=$t" }
                if (backup != null) {
                    cacheDaoWrapper.upsert(backup)
                }
                throw RepositoryHelperException("delete failed. query=$query", t)
            }
        }
    }
}

fun <ENTITY, READ_RESULT> RepositoryHelper<Unit, ENTITY, READ_RESULT>.select(): Flow<READ_RESULT> {
    return select(Unit)
}

suspend fun <ENTITY, READ_RESULT> RepositoryHelper<Unit, ENTITY, READ_RESULT>.refresh() {
    return refresh(Unit)
}
