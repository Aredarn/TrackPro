package com.example.trackpro.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    hint: String? = null
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message.uppercase(),
                style = TrackProType.titleMedium,
                // Was textFaint (3.35:1). An empty state is the only thing on the screen;
                // it is the last place that should be the dimmest text in the app.
                color = TrackProTheme.colors.marking
            )
            if (hint != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = hint,
                    style = TrackProType.body,
                    // Was textFaint at 70% alpha - composited to 2.21:1, the worst text
                    // contrast in the build, on first-run copy telling people what to do.
                    color = TrackProTheme.colors.markingDim
                )
            }
        }
    }
}
