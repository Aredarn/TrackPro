package com.example.trackpro.online.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.TrackProApp
import com.example.trackpro.components.DataGate
import com.example.trackpro.components.ScreenScaffold
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.managerClasses.utilities.toLapDeltaString
import com.example.trackpro.managerClasses.utilities.toLapTimeString
import com.example.trackpro.models.LoadState
import com.example.trackpro.online.AccountState
import com.example.trackpro.online.ApiException
import com.example.trackpro.online.ApiGpsSource
import com.example.trackpro.online.Leaderboard
import com.example.trackpro.online.LeaderboardEntry
import com.example.trackpro.online.NetworkException
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.atSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A track's TrackBoard leaderboard: every driver's best lap, fastest first.
 *
 * Public on the server, so this works signed out too; signing in only adds the driver's own
 * position when it falls below the top of the list.
 */
@Composable
fun LeaderboardScreen(trackId: Long, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as TrackProApp
    val online = app.online

    var state by remember { mutableStateOf<LoadState>(LoadState.Loading) }
    var board by remember { mutableStateOf<Leaderboard?>(null) }
    var trackName by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(trackId, reload) {
        state = LoadState.Loading
        state = try {
            val remoteId = withContext(Dispatchers.IO) {
                trackName = app.database.syncDao().getTrack(trackId)?.trackName
                online.remoteTrackIdFor(trackId)
            }
            // No server track yet: nobody has posted here. That is empty, not an error.
            board = remoteId?.let { online.api.getLeaderboard(online.auth.currentAccessTokenOrNull(), it, LIMIT) }
            LoadState.Ready
        } catch (e: NetworkException) {
            LoadState.Failed(e.message ?: "TrackBoard could not be reached.")
        } catch (e: ApiException) {
            LoadState.Failed(e.message ?: "TrackBoard refused the request.")
        }
    }

    val myId = (online.auth.state.value as? AccountState.SignedIn)?.userId
    val entries = board?.entries.orEmpty()
    // The driver's own entry, pinned below the list when it did not make the top.
    val myPinned = board?.me?.takeIf { me -> entries.none { it.userId == me.userId } }

    ScreenScaffold(
        title = "Leaderboard",
        subtitle = trackName,
        onBack = onBack
    ) { padding ->
        DataGate(
            state = state,
            items = entries,
            emptyMessage = "No laps posted yet",
            emptyHint = "Laps appear here once drivers who share their laps have driven this track.",
            loadingLabel = "Fetching",
            onRetry = { reload++ },
        ) { rows ->
            LazyColumn(
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = Spacing.xl)
            ) {
                items(rows, key = { it.userId }) { entry ->
                    EntryRow(entry, isMe = entry.userId == myId)
                }
                if (myPinned != null) {
                    item {
                        Text(
                            "Your best",
                            style = TrackProType.label,
                            color = TrackProTheme.colors.textMuted,
                            modifier = Modifier.padding(start = Spacing.lg, top = Spacing.lg, bottom = Spacing.xs)
                        )
                        EntryRow(myPinned, isMe = true)
                    }
                }
                item {
                    Text(
                        // Honest about provenance: these are self-reported, and the two GPS
                        // sources differ in precision.
                        "Lap times are posted by drivers and not verified. Phone GPS is less precise than an ESP32 module.",
                        style = TrackProType.body,
                        color = TrackProTheme.colors.textMuted,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.lg)
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: LeaderboardEntry, isMe: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Lets the lamp below fill the row's height, whatever the text wraps to.
            .height(IntrinsicSize.Min)
            .background(TrackProTheme.colors.bgCard),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The driver's own row carries the accent as a lamp at the edge, not a fill.
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(if (isMe) TrackProTheme.colors.accent else TrackProTheme.colors.bgCard)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.lg - 3.dp, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(
                text = entry.rank.toString(),
                style = TrackProType.titleLarge,
                // Purple is the sport's "fastest" — the leader's time, and nothing else.
                color = if (entry.rank == 1) TrackProTheme.colors.accent else TrackProTheme.colors.textPrimary,
                modifier = Modifier.width(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isMe) "${entry.displayName} · YOU" else entry.displayName,
                    style = TrackProType.titleMedium,
                    color = TrackProTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        entry.vehicle?.let { "${it.manufacturer} ${it.model} ${it.year}" },
                        sourceLabel(entry.gpsSource)
                    ).joinToString(" · "),
                    style = TrackProType.body,
                    color = TrackProTheme.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = entry.lapTimeMs.toLong().toLapTimeString(),
                    style = TrackProType.titleLarge,
                    color = if (entry.rank == 1) TrackProTheme.colors.accent else TrackProTheme.colors.textPrimary
                )
                if (entry.gapToLeaderMs > 0) {
                    Text(
                        text = entry.gapToLeaderMs.toLong().toLapDeltaString(),
                        style = TrackProType.body.atSize(12.sp),
                        color = TrackProTheme.colors.textMuted
                    )
                }
            }
        }
    }
    HorizontalDivider(color = TrackProTheme.colors.sectorLine, thickness = 1.dp)
}

private fun sourceLabel(source: ApiGpsSource): String = when (source) {
    ApiGpsSource.Wifi, ApiGpsSource.Bluetooth -> "ESP32"
    ApiGpsSource.PhoneGps -> "Phone GPS"
}

private const val LIMIT = 50
