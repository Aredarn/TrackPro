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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.trackpro.R
import com.example.trackpro.TrackProApp
import com.example.trackpro.components.ToggleChip
import com.example.trackpro.dataClasses.DragPublication
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.online.AccountState
import com.example.trackpro.online.SyncScheduler
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A drag run's line into TrackBoard: whether it is posted to the drag leaderboard, and the way
 * to post or withdraw it. Posting is per session, so nothing goes up without being asked for.
 */
@Composable
fun DragOnlineStrip(sessionId: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val online = app.online
    val scope = rememberCoroutineScope()
    val account by online.auth.state.collectAsState()
    val sharing by online.settings.sharingEnabled.collectAsState()
    val posted by remember(sessionId) { app.database.syncDao().observeIsDragPublished(sessionId) }
        .collectAsState(initial = false)

    val signedIn = account is AccountState.SignedIn
    val status = when {
        !signedIn -> stringResource(R.string.drag_strip_sign_in)
        !sharing -> stringResource(R.string.drag_strip_sharing_off)
        posted -> stringResource(R.string.drag_strip_posted)
        else -> stringResource(R.string.drag_strip_not_posted)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TrackProTheme.colors.bgCard)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.drag_strip_title), style = TrackProType.label, color = TrackProTheme.colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(status, style = TrackProType.body, color = TrackProTheme.colors.textMuted)
        }

        if (signedIn && sharing) {
            ToggleChip(
                text = if (posted) stringResource(R.string.drag_strip_withdraw) else stringResource(R.string.drag_strip_post),
                selected = posted,
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        val dao = app.database.syncDao()
                        if (posted) dao.unpublishDrag(sessionId)
                        else dao.publishDrag(DragPublication(sessionId, System.currentTimeMillis()))
                        SyncScheduler.syncSoon(context)
                    }
                }
            )
        }
    }
}
