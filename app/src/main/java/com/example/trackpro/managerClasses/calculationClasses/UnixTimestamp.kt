import java.text.SimpleDateFormat
import java.util.*

fun convertToUnixTimestamp(timestamp: String): Long {
    val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    val date = sdf.parse(timestamp) ?: return 0

    // Get current date
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, date.hours)
        set(Calendar.MINUTE, date.minutes)
        set(Calendar.SECOND, date.seconds)
        set(Calendar.MILLISECOND, (date.time % 1000).toInt())
    }

    return calendar.timeInMillis // Returns Unix timestamp in milliseconds
}
