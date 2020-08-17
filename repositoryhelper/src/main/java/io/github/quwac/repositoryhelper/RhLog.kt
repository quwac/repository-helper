package io.github.quwac.repositoryhelper

import android.util.Log


internal object RhLog {
    var isRepositoryHelperLogEnable = false

    inline fun d(message: () -> String) {
        if (isRepositoryHelperLogEnable) {
            Log.d("RepositoryHelper", message())
        }
    }
}