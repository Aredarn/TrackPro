package com.example.trackpro.models

/**
 * Whether a screen's data has been read yet, and whether the read worked.
 *
 * This exists because "empty" and "not looked yet" used to render identically. Every list
 * flow starts at `emptyList()`, so the first frame after navigation showed the empty state
 * - "No sessions recorded" - before the query returned. For a driver who has just lost a
 * session to a bug, that reads as confirmation it happened again.
 *
 * A list is only empty once [Ready] says the database has actually answered.
 */
sealed interface LoadState {
    data object Loading : LoadState
    data object Ready : LoadState
    /** [reason] is shown to the user, so it names the problem, not the exception class. */
    data class Failed(val reason: String) : LoadState
}
