package io.github.quwac.repositoryhelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

sealed class Result<out T> {
    companion object {
        @Suppress("DEPRECATION")
        private val loading = Loading<Any>()

        @Suppress("DEPRECATION")
        private val notFound = NotFound<Any>()

        @Suppress("UNCHECKED_CAST")
        fun <T> loading(): Loading<T> = loading as Loading<T>

        @Suppress("UNCHECKED_CAST")
        fun <T> notFound(): NotFound<T> = notFound as NotFound<T>
    }

    data class Success<out T>(val value: T) : Result<T>()
    data class Error<out T>(val exception: Throwable) : Result<T>()
    class Loading<out T> @Deprecated(
        "Use Result.loading",
        replaceWith = ReplaceWith("Result.loading<T>()")
    ) internal constructor() : Result<T>() {
        override fun toString(): String {
            return "Loading()"
        }
    }

    class NotFound<out T> @Deprecated(
        "Use Result.notFound",
        replaceWith = ReplaceWith("Result.loading<T>()")
    ) internal constructor() : Result<T>() {
        override fun toString(): String {
            return "NotFound()"
        }
    }
}

@Suppress("EXPERIMENTAL_API_USAGE")
fun <ENTITY> Flow<ENTITY?>.toResult(coroutineContext: CoroutineContext = GlobalScope.coroutineContext + Dispatchers.IO): Flow<Result<ENTITY>> {
    val source = this
    return channelFlow<Result<ENTITY>> {
        channel.offer(Result.loading())
        source.map {
            if (it != null) {
                Result.Success(it)
            } else {
                Result.notFound<ENTITY>()
            }
        }.collect {
            channel.offer(it)
            log("toResult", "offered: $it")
        }
    }.flowOn(coroutineContext)
}