package com.example.trackpro.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.models.LoadState
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim

/**
 * The three-state seal every data screen owes: reading, failed, empty, or content.
 *
 * Never two of those in one branch. Four list screens used to decide with a bare
 * `.isEmpty()`, which meant the frame before the database answered rendered "No sessions
 * recorded" - the app telling you your data was gone while it was still looking for it.
 *
 * Loading is deliberately a placard and not a spinner: a spinner says "something is
 * happening", a placard says what. On a panel, an instrument that has no reading yet shows
 * its own name and no value.
 */
@Composable
fun <T> DataGate(
    state: LoadState,
    items: List<T>,
    emptyMessage: String,
    emptyHint: String,
    modifier: Modifier = Modifier,
    loadingLabel: String = "Reading",
    onRetry: (() -> Unit)? = null,
    content: @Composable (List<T>) -> Unit
) {
    when {
        state is LoadState.Failed -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "READ FAILED",
                    style = TrackProType.titleMedium,
                    color = TrackProTheme.colors.deltaBad
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.reason,
                    style = TrackProType.body,
                    color = TrackProTheme.colors.markingDim,
                    textAlign = TextAlign.Center
                )
                if (onRetry != null) {
                    Spacer(Modifier.height(16.dp))
                    DashAction(
                        label = "Retry",
                        onClick = onRetry,
                        compact = true,
                        modifier = Modifier.width(160.dp)
                    )
                }
            }
        }

        state is LoadState.Loading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = loadingLabel.uppercase(),
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim
                )
                Spacer(Modifier.height(10.dp))
                // An unlit bar: the range is visible, the value is not known yet.
                SegmentBar(
                    signedFraction = 0f,
                    activeColor = TrackProTheme.colors.marking,
                    bidirectional = false,
                    segments = 12,
                    height = 8.dp,
                    modifier = Modifier.width(120.dp)
                )
            }
        }

        items.isEmpty() -> EmptyState(
            message = emptyMessage,
            hint = emptyHint,
            modifier = modifier.fillMaxWidth()
        )

        else -> content(items)
    }
}
