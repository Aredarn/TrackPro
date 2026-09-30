package com.example.trackpro.models

/**
 * A session's fastest completed lap, as stored ("MM:SS.hh"). One row per session that has
 * completed at least one lap.
 */
data class SessionBestLap(
    val sessionId: Long,
    val bestLap: String
)
