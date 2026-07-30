package com.example.trackpro.managerClasses.utilities

// Lap times throughout the app are stored/displayed as "MM:SS.hh" (hundredths of a
// second, 2 digits) - see TimingManager.formatTime / TimeAttackViewModel.formatLapTime.
// These must stay exact inverses of that format and of each other.
fun String.toLapTimeMillis(): Long {
    val parts = this.split(":", ".", limit = 3)
    val minutes = parts.getOrNull(0)?.toLongOrNull() ?: 0L
    val seconds = parts.getOrNull(1)?.toLongOrNull() ?: 0L
    val hundredths = parts.getOrNull(2)?.toLongOrNull() ?: 0L
    return minutes * 60_000 + seconds * 1_000 + hundredths * 10
}

fun Long.toLapTimeString(): String {
    val abs = if (this < 0) -this else this
    val minutes = abs / 60_000
    val seconds = (abs % 60_000) / 1_000
    val hundredths = (abs % 1_000) / 10
    return String.format("%02d:%02d.%02d", minutes, seconds, hundredths)
}
