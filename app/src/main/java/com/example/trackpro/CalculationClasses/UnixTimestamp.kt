import java.text.SimpleDateFormat
import java.util.*

fun convertToUnixTimestamp(timestamp: String): Long {
    val calendar = Calendar.getInstance()
    val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    val date = sdf.parse(timestamp) ?: return 0

    calendar.set(Calendar.HOUR_OF_DAY, date.hours)
    calendar.set(Calendar.MINUTE, date.minutes)
    calendar.set(Calendar.SECOND, date.seconds)
    calendar.set(Calendar.MILLISECOND, (date.time % 1000).toInt())

    return calendar.timeInMillis  // Full UNIX timestamp
}
