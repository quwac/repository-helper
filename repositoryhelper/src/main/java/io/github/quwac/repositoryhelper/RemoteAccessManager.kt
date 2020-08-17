package io.github.quwac.repositoryhelper

import java.util.concurrent.ConcurrentHashMap


open class RemoteAccessManager(private val thresholdMillis: Long) {
    private val queryHashToLastReceived = ConcurrentHashMap<String, Long>()

    open fun canAccessNow(queryHash: String, now: Long = System.currentTimeMillis()): Boolean {
        val lastReceived = queryHashToLastReceived[queryHash]
        return if (lastReceived != null) {
            lastReceived + thresholdMillis < now
        } else {
            true
        }
    }

    open fun logAccess(hash: String, timeMillis: Long = System.currentTimeMillis()) {
        queryHashToLastReceived[hash] = timeMillis
    }
}