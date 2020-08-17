package io.github.quwac.repositoryhelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

@Suppress("EXPERIMENTAL_API_USAGE")
class RepositoryHelper<QUERY, ENTITY, READ_RESULT, CACHE_WRITE_RESULT, SERVER_WRITE_RESULT> private constructor(
    private val coroutineContext: CoroutineContext = GlobalScope.coroutineContext + Dispatchers.IO,
    private val selectFlowFromCache: SelectFlow<QUERY, READ_RESULT>,
    private val selectRawFromCache: SelectRaw<QUERY, ENTITY>,
    private val selectRawFromServer: SelectRaw<QUERY, ENTITY>,
    private val upsertToCache: Upsert<ENTITY, CACHE_WRITE_RESULT>,
    private val upsertToServer: Upsert<ENTITY, SERVER_WRITE_RESULT>,
    private val deleteFromCache: Delete<QUERY, CACHE_WRITE_RESULT>,
    private val deleteFromServer: Delete<QUERY, SERVER_WRITE_RESULT>,
    private val onErrorReturn: (READ_RESULT, Throwable) -> READ_RESULT,
    private val remoteAccessManager: RemoteAccessManager = RemoteAccessManager(
        3000
    ),
    private val queryToHash: QueryToHash<QUERY> = { it.toString() }
) {
    companion object {
        @Suppress("MemberVisibilityCanBePrivate")
        fun <QUERY, ENTITY, READ_RESULT, CACHE_WRITE_RESULT, SERVER_WRITE_RESULT> build(
            coroutineContext: CoroutineContext = GlobalScope.coroutineContext + Dispatchers.IO,
            selectFlowFromCache: SelectFlow<QUERY, READ_RESULT>,
            selectRawFromCache: SelectRaw<QUERY, ENTITY>,
            selectRawFromServer: SelectRaw<QUERY, ENTITY>,
            upsertToCache: Upsert<ENTITY, CACHE_WRITE_RESULT>,
            upsertToServer: Upsert<ENTITY, SERVER_WRITE_RESULT>,
            deleteFromCache: Delete<QUERY, CACHE_WRITE_RESULT>,
            deleteFromServer: Delete<QUERY, SERVER_WRITE_RESULT>,
            onErrorReturn: (READ_RESULT, Throwable) -> READ_RESULT,
            remoteAccessManager: RemoteAccessManager = RemoteAccessManager(
                3000
            ),
            queryToHash: QueryToHash<QUERY> = { it.toString() }
        ): RepositoryHelper<QUERY, ENTITY, READ_RESULT, CACHE_WRITE_RESULT, SERVER_WRITE_RESULT> =
            RepositoryHelper(
                coroutineContext = coroutineContext,
                selectFlowFromCache = selectFlowFromCache,
                selectRawFromCache = selectRawFromCache,
                selectRawFromServer = selectRawFromServer,
                upsertToCache = upsertToCache,
                upsertToServer = upsertToServer,
                deleteFromCache = deleteFromCache,
                deleteFromServer = deleteFromServer,
                onErrorReturn = onErrorReturn,
                remoteAccessManager = remoteAccessManager,
                queryToHash = queryToHash
            )

        fun <QUERY, ENTITY, READ_RESULT> build(
            coroutineContext: CoroutineContext = GlobalScope.coroutineContext + Dispatchers.IO,
            cacheDao: CacheDaoWrapper<QUERY, ENTITY, READ_RESULT, Any>,
            serverDao: ServerDaoWrapper<QUERY, ENTITY, Any>,
            onErrorReturn: (READ_RESULT, Throwable) -> READ_RESULT,
            remoteAccessManager: RemoteAccessManager = RemoteAccessManager(
                3000
            ),
            queryToHash: QueryToHash<QUERY> = { it.toString() }
        ): RepositoryHelper<QUERY, ENTITY, READ_RESULT, Any, Any> = build(
            coroutineContext = coroutineContext,
            selectFlowFromCache = cacheDao::selectFlow,
            selectRawFromCache = cacheDao::selectRaw,
            selectRawFromServer = serverDao::select,
            upsertToCache = cacheDao::upsert,
            upsertToServer = serverDao::upsert,
            deleteFromCache = cacheDao::delete,
            deleteFromServer = serverDao::delete,
            onErrorReturn = onErrorReturn,
            remoteAccessManager = remoteAccessManager,
            queryToHash = queryToHash
        )

        fun <QUERY, ENTITY, READ_RESULT, CACHE_WRITE_RESULT, SERVER_WRITE_RESULT> buildCustomWriteResult(
            coroutineContext: CoroutineContext = GlobalScope.coroutineContext + Dispatchers.IO,
            cacheDao: CacheDaoWrapper<QUERY, ENTITY, READ_RESULT, CACHE_WRITE_RESULT>,
            serverDao: ServerDaoWrapper<QUERY, ENTITY, SERVER_WRITE_RESULT>,
            onErrorReturn: (READ_RESULT, Throwable) -> READ_RESULT,
            remoteAccessManager: RemoteAccessManager = RemoteAccessManager(
                3000
            ),
            queryToHash: QueryToHash<QUERY> = { it.toString() }
        ): RepositoryHelper<QUERY, ENTITY, READ_RESULT, CACHE_WRITE_RESULT, SERVER_WRITE_RESULT> =
            build(
                coroutineContext = coroutineContext,
                selectFlowFromCache = cacheDao::selectFlow,
                selectRawFromCache = cacheDao::selectRaw,
                selectRawFromServer = serverDao::select,
                upsertToCache = cacheDao::upsert,
                upsertToServer = serverDao::upsert,
                deleteFromCache = cacheDao::delete,
                deleteFromServer = serverDao::delete,
                onErrorReturn = onErrorReturn,
                remoteAccessManager = remoteAccessManager,
                queryToHash = queryToHash
            )
    }

    private val mutexManager = MutexManager(queryToHash)

    fun select(query: QUERY): Flow<READ_RESULT> {
        val cacheFlow = selectFlowFromCache(query)

        val queryHash = queryToHash(query)
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
                    val entity = selectRawFromServer(query).also {
                        RhLog.d { "accessServer: entity=$it" }
                    }
                    if (entity != null) {
                        upsertToCache(entity)
                    } else {
                        deleteFromCache(query)
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

    suspend fun upsert(query: QUERY, newValue: ENTITY): CACHE_WRITE_RESULT {
        val mutex = mutexManager.get(query)
        mutex.withLock {
            val backup = selectRawFromCache(query)

            val result = upsertToCache(newValue)

            return try {
                upsertToServer(newValue)
                result
            } catch (t: Throwable) {
                RhLog.d { "upsert: backup=$backup, t=$t" }
                if (backup != null) {
                    upsertToCache(backup)
                } else {
                    deleteFromCache(query)
                }
                throw RepositoryHelperException("upsert failed. query=$query,newValue=$newValue", t)
            }
        }
    }

    suspend fun delete(
        query: QUERY
    ): CACHE_WRITE_RESULT {
        val mutex = mutexManager.get(query)
        mutex.withLock {
            val backup = selectRawFromCache(query)

            val result = deleteFromCache(query)

            return try {
                deleteFromServer(query)
                result
            } catch (t: Throwable) {
                RhLog.d { "delete: backup=$backup, t=$t" }
                if (backup != null) {
                    upsertToCache(backup)
                }
                throw RepositoryHelperException("delete failed. query=$query", t)
            }
        }
    }
}