package io.github.quwac.repositoryhelper

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.WeakHashMap

class MutexManager<QUERY>(
    private val queryToHash: (query: QUERY) -> String
) {
    private val keyToMutex = WeakHashMap<String, Mutex>()
    private val mutex = Mutex()

    suspend fun get(query: QUERY): Mutex {
        val key = queryToHash(query)
        mutex.withLock {
            return keyToMutex[key] ?: Mutex().also {
                keyToMutex[key] = it
            }
        }
    }

}
