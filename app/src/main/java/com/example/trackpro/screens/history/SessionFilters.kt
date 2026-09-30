package com.example.trackpro.screens.history

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import java.time.ZonedDateTime

/** How far back the History list reaches. Calendar days, counting today. */
enum class SessionPeriod(val label: String) {
    ALL("All time"),
    LAST_7_DAYS("Last 7 days"),
    LAST_30_DAYS("Last 30 days"),
    THIS_YEAR("This year");

    /**
     * The earliest start time this period includes, or null for no limit.
     *
     * Days are calendar days in the phone's time zone, not rolling 24-hour blocks: "last 7
     * days" run from midnight six days ago, so a session from the morning of that day is
     * in however late in today it is asked.
     */
    fun startsFrom(now: ZonedDateTime): Long? {
        val today = now.toLocalDate()
        val from = when (this) {
            ALL -> return null
            LAST_7_DAYS -> today.minusDays(6)
            LAST_30_DAYS -> today.minusDays(29)
            THIS_YEAR -> today.withDayOfYear(1)
        }
        return from.atStartOfDay(now.zone).toInstant().toEpochMilli()
    }
}

/** The order sessions are listed in. */
enum class SessionSort(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    /**
     * By each session's best lap. Only meaningful within one track - a lap of a short circuit
     * is not faster than one of a long circuit in any useful sense - so it orders the sessions
     * inside each track, while the tracks themselves stay in order of recent use.
     */
    FASTEST("Fastest lap");
}

/**
 * What the History list is narrowed to, and in what order. Null ids mean "any".
 */
data class SessionFilter(
    val vehicleId: Long? = null,
    val trackId: Long? = null,
    val period: SessionPeriod = SessionPeriod.ALL,
    val sort: SessionSort = SessionSort.NEWEST
) {
    /** Whether anything is being hidden. The sort order hides nothing, so it does not count. */
    val narrows: Boolean
        get() = vehicleId != null || trackId != null || period != SessionPeriod.ALL

    /** Drops every filter but keeps the chosen order. */
    fun cleared(): SessionFilter = SessionFilter(sort = sort)
}

/** The parts of a session the filters look at, whatever list it came from. */
data class SessionKey(
    val vehicleId: Long?,
    val trackId: Long?,
    val startTime: Long,
    /** Best completed lap in ms, or null for a session without one (and for drag runs). */
    val bestLapMs: Long? = null
)

/**
 * The sessions [filter] lets through, in its order.
 *
 * Generic over the row type because the two History sections list different things - track
 * sessions and drag runs - and should narrow and order them the same way.
 */
fun <T> List<T>.applySessionFilter(
    filter: SessionFilter,
    now: ZonedDateTime,
    keyOf: (T) -> SessionKey
): List<T> {
    val from = filter.period.startsFrom(now)
    return filter { item ->
        val key = keyOf(item)
        (filter.vehicleId == null || key.vehicleId == filter.vehicleId) &&
                (filter.trackId == null || key.trackId == filter.trackId) &&
                (from == null || key.startTime >= from)
    }.sortedWith(sessionOrder(filter.sort, keyOf))
}

private fun <T> sessionOrder(sort: SessionSort, keyOf: (T) -> SessionKey): Comparator<T> =
    when (sort) {
        SessionSort.NEWEST -> compareByDescending { keyOf(it).startTime }
        SessionSort.OLDEST -> compareBy { keyOf(it).startTime }
        // Sessions that never completed a lap have nothing to rank on; they go last, newest
        // first among themselves.
        SessionSort.FASTEST -> compareBy<T, Long?>(nullsLast()) { keyOf(it).bestLapMs }
            .thenByDescending { keyOf(it).startTime }
    }

/**
 * Splits already-ordered sessions into groups (a track, a day), keeping each group's members
 * in that order.
 *
 * Groups come in order of their first member, so "newest first" also puts the most recently
 * used group first. The exception is [SessionSort.FASTEST]: best laps on different tracks do
 * not compare, so there the groups are ordered by their most recent session instead.
 */
fun <T, G> List<T>.groupSessions(
    sort: SessionSort,
    keyOf: (T) -> SessionKey,
    groupOf: (T) -> G
): List<Pair<G, List<T>>> {
    val groups = groupBy(groupOf).toList()
    return if (sort == SessionSort.FASTEST) {
        groups.sortedByDescending { (_, members) -> members.maxOf { keyOf(it).startTime } }
    } else {
        groups
    }
}

private const val NONE = -1L

/**
 * Keeps a filter across rotation and while a session is open on top of the list. Ids are
 * never negative, so -1 can stand for "any" in the saved form.
 */
val SessionFilterSaver: Saver<SessionFilter, Any> = listSaver(
    save = { listOf(it.vehicleId ?: NONE, it.trackId ?: NONE, it.period.name, it.sort.name) },
    restore = { saved ->
        SessionFilter(
            vehicleId = (saved[0] as Long).takeIf { it != NONE },
            trackId = (saved[1] as Long).takeIf { it != NONE },
            period = runCatching { SessionPeriod.valueOf(saved[2] as String) }.getOrDefault(SessionPeriod.ALL),
            sort = runCatching { SessionSort.valueOf(saved[3] as String) }.getOrDefault(SessionSort.NEWEST)
        )
    }
)
