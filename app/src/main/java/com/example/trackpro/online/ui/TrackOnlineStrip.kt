package com.example.trackpro.online.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.trackpro.TrackProApp
import com.example.trackpro.components.ToggleChip
import com.example.trackpro.dataClasses.TrackPublication
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.online.AccountState
import com.example.trackpro.online.SyncScheduler
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The track screen's line into TrackBoard: whether this track has a leaderboard, a way to
 * publish one the driver built, and the way in.
 *
 * Premade tracks are shared by every install automatically, so they only ever offer "Open".
 */
@Composable
fun TrackOnlineStrip(trackId: Long, onOpenLeaderboard: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val online = app.online
    val scope = rememberCoroutineScope()
    val account by online.auth.state.collectAsState()
    val published by remember(trackId) { app.database.syncDao().observeIsPublished(trackId) }
        .collectAsState(initial = false)

    // Null until known; the strip stays neutral rather than briefly claiming the wrong thing.
    var premade by remember(trackId) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(trackId) {
        premade = withContext(Dispatchers.IO) { online.isPremade(trackId) }
    }

    val signedIn = account is AccountState.SignedIn
    val status = when {
        premade == null -> ""
        premade == true -> "Shared track · every TrackPro driver"
        published -> "Published by you"
        signedIn -> "Private · publish it to open a leaderboard"
        else -> "Private · sign in under Settings to publish"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrackProTheme.colors.bgCard)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Leaderboard", style = TrackProType.label, color = TrackProTheme.colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(status, style = TrackProType.body, color = TrackProTheme.colors.textMuted)
        }

        if (premade == false && signedIn) {
            ToggleChip(
                text = if (published) "Unpublish" else "Publish",
                selected = published,
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val dao = app.database.syncDao()
                        if (published) dao.unpublish(trackId)
                        else dao.publish(TrackPublication(trackId, System.currentTimeMillis()))
                        SyncScheduler.syncSoon(context)
                    }
                }
            )
        }

        // Drawn disabled rather than hidden, so the control's place is learned before it works.
        val canOpen = premade == true || published
        ToggleChip(
            text = "Open",
            selected = canOpen,
            onClick = { if (canOpen) onOpenLeaderboard() }
        )
    }
}
