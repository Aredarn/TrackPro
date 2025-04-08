class UiDelayTracker {
    private val delays = mutableListOf<Long>()
    private var maxDelay = 0L
    private var minDelay = Long.MAX_VALUE
    private var totalDelay = 0L
    private var count = 0

    fun trackDelay(gpsTimestamp: Long): Long {
        val currentTime = System.currentTimeMillis()
        val delay = currentTime - gpsTimestamp

        // Update statistics
        delays.add(delay)
        maxDelay = maxOf(maxDelay, delay)
        minDelay = minOf(minDelay, delay)
        totalDelay += delay
        count++

        return delay
    }

    fun getAverageDelay(): Double = if (count > 0) totalDelay.toDouble() / count else 0.0
    fun getMaxDelay(): Long = maxDelay
    fun getMinDelay(): Long = if (count > 0) minDelay else 0
}