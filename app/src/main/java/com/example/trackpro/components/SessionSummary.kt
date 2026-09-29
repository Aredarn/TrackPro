package com.example.trackpro.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProShapes
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.accentGlow
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.panel
import java.util.Locale

/**
 * A session that beat the driver's previous best on this track, or set the first time there.
 * [previousMs] is null for a first time on the track.
 */
data class PersonalBestMoment(val newMs: Long, val previousMs: Long?)

/**
 * What the driver sees after a stint: the end of the experience, so it closes with a
 * summary rather than a silent save.
 *
 * When the session set a personal best this is also the peak: a trophy that springs in with
 * a soft orange glow and says by how much. Nothing moves while driving - the celebration
 * waits for this screen, where there is attention to spare.
 *
 * It is the one place a run is voided, decided while the run is still in mind. Keep is the
 * default and the larger target.
 */
@Composable
fun SessionSummary(
    headline: String,
    headlineCaption: String,
    rows: List<Pair<String, String>>,
    onKeep: () -> Unit,
    onVoid: () -> Unit,
    modifier: Modifier = Modifier,
    saveFailed: String? = null,
    personalBest: PersonalBestMoment? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TrackProTheme.colors.panel)
            .statusBarsPadding()
            .padding(horizontal = Spacing.gutter)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Spacer(Modifier.height(Spacing.xl))

            when {
                saveFailed != null -> StatusHeader(Icons.Default.ErrorOutline, "Session not saved", TrackProTheme.colors.danger)
                personalBest != null -> PersonalBestCelebration(personalBest)
                else -> StatusHeader(Icons.Default.CheckCircle, "Session complete", TrackProTheme.colors.deltaGood)
            }

            PaddockCard {
                Text(headlineCaption, style = TrackProType.label, color = TrackProTheme.colors.markingDim)
                Text(
                    headline,
                    style = TrackProType.displayNumeric.atSize(56.sp),
                    color = if (personalBest != null) TrackProTheme.colors.accent else TrackProTheme.colors.marking
                )
            }

            if (rows.isNotEmpty()) {
                PaddockCard(padding = 0.dp) {
                    rows.forEachIndexed { i, (label, value) ->
                        if (i > 0) Bezel(Modifier.padding(horizontal = Spacing.lg))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, style = TrackProType.body, color = TrackProTheme.colors.markingDim, modifier = Modifier.weight(1f))
                            Text(value, style = TrackProType.titleMedium, color = TrackProTheme.colors.marking)
                        }
                    }
                }
            }

            if (saveFailed != null) {
                Text(
                    text = saveFailed,
                    style = TrackProType.label,
                    color = TrackProTheme.colors.marking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TrackProShapes.control)
                        .background(TrackProTheme.colors.danger.copy(alpha = 0.14f))
                        .padding(Spacing.lg)
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            PrimaryButton(
                text = "Keep session",
                onClick = onKeep,
                haptic = Haptic.Confirm,
                modifier = Modifier.fillMaxWidth()
            )
            DashAction(
                label = "Void this run",
                onClick = onVoid,
                compact = true,
                accent = TrackProTheme.colors.danger,
                icon = Icons.Default.Block,
                haptic = Haptic.Reject
            )
            Text(
                text = "A voided run is kept but marked, and stops counting toward your bests.",
                style = TrackProType.label,
                color = TrackProTheme.colors.markingDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatusHeader(icon: ImageVector, text: String, tone: Color) {
    Row(
        modifier = Modifier
            .clip(TrackProShapes.pill)
            .background(tone.copy(alpha = 0.14f))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(Spacing.sm))
        Text(text, style = TrackProType.label.copy(fontWeight = FontWeight.SemiBold), color = tone)
    }
}

/**
 * The peak: a trophy springing in on a soft glow, the words "New personal best", and the
 * margin. Proportional - a spring and a glow, no confetti - because it is a timing app.
 */
@Composable
private fun PersonalBestCelebration(moment: PersonalBestMoment) {
    val reducedMotion = rememberReducedMotion()
    var shown by remember { mutableStateOf(reducedMotion) }
    LaunchedEffect(Unit) { shown = true }

    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "trophyScale"
    )
    val fade by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(360),
        label = "trophyFade"
    )
    val glow = TrackProTheme.colors.accentGlow

    PaddockCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(Spacing.sm))
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale; alpha = fade }
                    .shadow(28.dp, CircleShape, clip = false, ambientColor = glow, spotColor = glow)
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(TrackProTheme.colors.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = TrackProTheme.colors.onAccent, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                if (moment.previousMs == null) "First time on the board" else "New personal best",
                style = TrackProType.titleLarge,
                color = TrackProTheme.colors.marking,
                modifier = Modifier.graphicsLayer { alpha = fade }
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = moment.previousMs?.let { prev ->
                    val gain = (prev - moment.newMs) / 1000.0
                    "${String.format(Locale.US, "%.2f", gain)} s faster than your previous best on this track"
                } ?: "Your first timed lap on this track. That's the one to beat now.",
                style = TrackProType.body,
                color = TrackProTheme.colors.markingDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = fade }
            )
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}
