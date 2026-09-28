package com.example.trackpro.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/**
 * Holds the display on for as long as this is in composition with [enabled] true.
 *
 * For anything that records. In a windscreen mount nobody touches the phone, so it slept on
 * its normal timeout - usually well inside one lap. That turned the HUD off mid-session and,
 * worse, backgrounded the app, and a backgrounded app gets phone-GPS updates only a few
 * times an hour. RecordingService covers the phone being locked on purpose; this covers it
 * never being touched.
 *
 * Only while [enabled], so a screen that merely *can* record - one waiting for Start - does
 * not keep the display lit and flatten the battery while nothing is happening.
 */
@Composable
fun KeepScreenOn(enabled: Boolean = true) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        ScreenOnRequests.count += 1
        view.keepScreenOn = true
        onDispose {
            ScreenOnRequests.count -= 1
            if (ScreenOnRequests.count == 0) view.keepScreenOn = false
        }
    }
}

/**
 * Outstanding requests to keep the display on.
 *
 * Counted rather than a plain flag because every screen shares the one ComposeView, and a
 * navigation cross-fade keeps the outgoing and incoming screens composed at the same time.
 * With a flag, a recording screen being left could switch the display back off after the
 * recording screen replacing it had just switched it on. Effects run on the main thread, so
 * the count needs no synchronisation.
 */
private object ScreenOnRequests {
    var count = 0
}
