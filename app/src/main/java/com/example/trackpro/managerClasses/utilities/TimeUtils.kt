package com.example.trackpro.managerClasses.utilities

fun String.toLapTimeMillis(): Long {
    val parts = this.split(":", ".", limit = 3)
    val minutes = parts.getOrNull(0)?.toLongOrNull() ?: 0L
    val seconds = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    val millis = parts.getOrNull(2)?.toLongOrNull() ?: 0L
    return minutes * 60_000 + seconds * 1_000 + millis
}

fun Long.toLapTimeString(): String {
    val abs = if (this < 0) -this else this
    val minutes = abs / 60_000
    val seconds = (abs % 60_000) / 1_000
    val millis = abs % 1_000
    return String.format("%02d:%02d.%03d", minutes, seconds, millis)
}
