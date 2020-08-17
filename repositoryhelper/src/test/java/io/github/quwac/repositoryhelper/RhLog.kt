package io.github.quwac.repositoryhelper

import android.util.Log

private val buf = StringBuilder().also {
    it.append("----------START----------\n")
}
private val locker = Any()

fun log(tag: String, text: String) {
    synchronized(locker) {
        buf.append(System.currentTimeMillis()).append("/")
            .append(tag).append(": ").append(text).append("\n")
    }
}

fun outputLog() {
    synchronized(locker) {
        buf.append("----------END----------")
        Log.e("outputLog", buf.toString())
        buf.clear()
    }
}