package com.example.trackpro.managerClasses

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.trackpro.R
import com.example.trackpro.TrackProApp
import com.example.trackpro.models.GpsProviderType

/**
 * Keeps a recording alive while the phone is locked, pocketed, or showing another app.
 *
 * The recording itself runs in the screens' ViewModels; this service does no work of its
 * own. What it provides is foreground status. Without it, the moment the screen went off
 * the app counted as backgrounded, and Android throttles a backgrounded app's location
 * updates to a few per *hour* - so with phone GPS, laps simply stopped registering, and the
 * process itself became a candidate for being killed mid-session.
 *
 * Screens that record also keep the display on (see KeepScreenOn), which covers a phone left
 * in a mount. This covers everything that does not: the lock button, a pocket, a glance at
 * another app.
 *
 * Deliberately unable to break a recording. Every precondition Android enforces is checked
 * before asking to enter the foreground, and a refusal anyway - on an Android version or OEM
 * build that behaves differently - is caught and logged. The worst case is the recording
 * running exactly as it did before this service existed.
 */
class RecordingService : Service() {

    // Its notification is the one piece of text shown outside the app, in the app's language.
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.example.trackpro.managerClasses.utilities.AppLanguage.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val phoneGps = intent?.getBooleanExtra(EXTRA_PHONE_GPS, false) ?: false
        try {
            ensureChannel()
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // The type has to name what is being kept alive. Phone GPS is location; the
                // ESP32 over Wi-Fi or Bluetooth is an external connected device.
                val type = if (phoneGps) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                }
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not enter the foreground; recording continues unprotected", e)
            stopSelf()
        }
        // Not sticky: if the process dies, the recording it protected died with it, and there
        // is nothing for a restarted service to keep alive.
        return START_NOT_STICKY
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        // Created or renamed: an existing channel takes the new name, so it follows the app's language.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
            }
        )
    }

    private fun buildNotification(): Notification {
        // The launcher intent resumes the existing task exactly as tapping the app icon does,
        // which puts the driver straight back on the screen that is recording.
        val returnToApp = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            PendingIntent.getActivity(
                this,
                0,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_recording)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(returnToApp)
            .build()
    }

    companion object {
        private const val TAG = "RecordingService"
        private const val CHANNEL_ID = "session_recording"
        private const val NOTIFICATION_ID = 4210
        private const val EXTRA_PHONE_GPS = "phone_gps"

        /**
         * Everything currently recording. Counted rather than a flag because a time attack
         * session and a drag run can both be alive at once - the time attack screen keeps its
         * session while another screen is pushed on top of it - and one of them finishing
         * must not drop the other's protection.
         */
        private val holders = mutableSetOf<String>()

        /** Registers [holder] as recording; enters the foreground if it is the first. */
        fun acquire(context: Context, holder: String) {
            val first = synchronized(holders) { holders.add(holder) && holders.size == 1 }
            if (first) start(context.applicationContext)
        }

        /** Unregisters [holder]; leaves the foreground once nothing is recording. */
        fun release(context: Context, holder: String) {
            val last = synchronized(holders) { holders.remove(holder) && holders.isEmpty() }
            if (last) {
                val appContext = context.applicationContext
                appContext.stopService(Intent(appContext, RecordingService::class.java))
            }
        }

        private fun start(context: Context) {
            val app = context as? TrackProApp ?: return
            val phoneGps = app.gpsSource.value == GpsProviderType.PHONE_GPS

            // A location-type foreground service is refused outright without the location
            // permission - and without it phone GPS is producing nothing to protect anyway.
            // The connected-device type needs one of a list of permissions, which the manifest
            // satisfies with CHANGE_WIFI_STATE, granted at install.
            if (phoneGps && !hasLocationPermission(context)) {
                Log.i(TAG, "No location permission; recording without foreground protection")
                return
            }

            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, RecordingService::class.java).putExtra(EXTRA_PHONE_GPS, phoneGps)
                )
            } catch (e: Exception) {
                // e.g. ForegroundServiceStartNotAllowedException if this somehow ran while the
                // app was already in the background. Recording goes on regardless.
                Log.w(TAG, "Could not start foreground protection: ${e.message}")
            }
        }

        private fun hasLocationPermission(context: Context): Boolean =
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }
}
