package com.example.trackpro.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.field
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.panel

/**
 * What the driver sees in the five seconds after a stint.
 *
 * Previously: nothing. Back was tapped, the session saved silently in `onCleared()`, and
 * the panel reappeared showing dashes until an async query landed. That is the emotional
 * peak of the whole product and the one moment the app had the driver's full attention,
 * and it said nothing at all.
 *
 * This is also the only correct home for VOID. A discard decision is made here, once,
 * while the run is still in mind - not later from a list where every session looks the
 * same. Keep is the default and the larger target; voiding is deliberate.
 */
@Composable
fun SessionSummary(
    headline: String,
    headlineCaption: String,
    rows: List<Pair<String, String>>,
    onKeep: () -> Unit,
    onVoid: () -> Unit,
    modifier: Modifier = Modifier,
    saveFailed: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.panel)
    ) {
        Spacer(Modifier.weight(1f))

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = if (saveFailed != null) "Session not saved" else "Session complete",
                style = TrackProType.label,
                color = if (saveFailed != null) TrackProTheme.colors.danger
                else TrackProTheme.colors.deltaGood
            )
            Spacer(Modifier.height(12.dp))
            Readout(
                value = headline,
                caption = headlineCaption,
                valueColor = TrackProTheme.colors.accent,
                valueSize = 64.sp
            )
        }

        Spacer(Modifier.height(20.dp))
        Bezel()

        rows.forEachIndexed { i, (label, value) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Instrument(label = label, value = value, valueSize = 20.sp, modifier = Modifier.weight(1f))
            }
            if (i < rows.lastIndex) Bezel()
        }
        Bezel()

        if (saveFailed != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrackProTheme.colors.field)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = saveFailed,
                    style = TrackProType.body.atSize(12.sp),
                    color = TrackProTheme.colors.danger
                )
            }
            Bezel()
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DashAction(
                label = "Keep",
                detail = "Save to the archive",
                onClick = onKeep,
                haptic = Haptic.Confirm
            )
            DashAction(
                label = "Void this run",
                onClick = onVoid,
                compact = true,
                accent = TrackProTheme.colors.danger,
                haptic = Haptic.Reject
            )
            Text(
                text = "A voided run is kept but stamped, and stops counting toward your bests.",
                style = TrackProType.label,
                color = TrackProTheme.colors.markingDim,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
