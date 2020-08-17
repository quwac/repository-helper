package io.github.quwac.repositoryhelper

fun <T> onErrorResultReturn(result: Result<T>, e: Throwable): Result<T> {
    return if (result is Result.NotFound) {
        Result.Error(e)
    } else {
        result
    }
}