package com.example.trackpro.online.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.trackpro.TrackProApp
import com.example.trackpro.components.DashGroup
import com.example.trackpro.components.PrimaryButton
import com.example.trackpro.components.ToggleChip
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.online.AccountState
import com.example.trackpro.online.ApiException
import com.example.trackpro.online.NetworkException
import com.example.trackpro.online.SyncReport
import com.example.trackpro.online.SyncScheduler
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The Settings group for TrackBoard: server, account, leaderboard sharing, and sync status.
 *
 * Everything here is optional. The copy says so first, because the product promise is that
 * nothing in TrackPro depends on an account or a network.
 */
@Composable
fun OnlineSettingsGroup() {
    val context = LocalContext.current
    val online = (context.applicationContext as TrackProApp).online
    val account by online.auth.state.collectAsState()
    val serverUrl by online.settings.serverUrl.collectAsState()

    DashGroup("Online · TrackBoard") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                "Optional. Post lap times to per-track leaderboards. Recording never needs an account or a network.",
                style = TrackProType.body,
                color = TrackProTheme.colors.textMuted
            )

            ServerRow(url = serverUrl, onChange = online.settings::setServerUrl)

            when (val state = account) {
                is AccountState.SignedOut -> SignInForm(
                    notice = state.notice,
                    serverSet = serverUrl.isNotBlank()
                )
                is AccountState.SignedIn -> SignedInRows(state)
            }
        }
    }
}

@Composable
private fun ServerRow(url: String, onChange: (String) -> Unit) {
    Column {
        Placard("Server")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = url,
            onValueChange = onChange,
            placeholder = { Text("e.g. http://10.0.2.2:5000", color = TrackProTheme.colors.textMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors()
        )
    }
}

@Composable
private fun SignInForm(notice: String?, serverSet: Boolean) {
    val context = LocalContext.current
    val online = (context.applicationContext as TrackProApp).online
    val scope = rememberCoroutineScope()

    var creating by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    // Deliberately not rememberSaveable: a password should not outlive the screen in a bundle.
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Placard("Account")

        // An involuntary sign-out (expired session) is a fault the driver needs to see.
        notice?.let { Text(it, style = TrackProType.body, color = TrackProTheme.colors.deltaBad) }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ToggleChip(
                text = "Sign in",
                selected = !creating,
                onClick = { creating = false; error = null },
                modifier = Modifier.weight(1f)
            )
            ToggleChip(
                text = "Create account",
                selected = creating,
                onClick = { creating = true; error = null },
                modifier = Modifier.weight(1f)
            )
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email", color = TrackProTheme.colors.textMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors()
        )

        if (creating) {
            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Name on leaderboards", color = TrackProTheme.colors.textMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
        }

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(if (creating) "Password (12+ characters)" else "Password", color = TrackProTheme.colors.textMuted) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors()
        )

        error?.let { Text(it, style = TrackProType.body, color = TrackProTheme.colors.deltaBad) }

        if (!serverSet) {
            Text("Set a server above first.", style = TrackProType.body, color = TrackProTheme.colors.textMuted)
        }

        val formComplete = email.isNotBlank() && password.isNotEmpty() && (!creating || displayName.isNotBlank())

        PrimaryButton(
            text = when {
                busy -> "Working…"
                creating -> "Create account"
                else -> "Sign in"
            },
            enabled = serverSet && formComplete && !busy,
            onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        if (creating) online.auth.register(email, displayName, password)
                        else online.auth.signIn(email, password)
                        password = ""
                        SyncScheduler.schedulePeriodic(context)
                        SyncScheduler.syncSoon(context)
                    } catch (e: ApiException) {
                        error = e.message
                    } catch (e: NetworkException) {
                        error = e.message
                    } finally {
                        busy = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SignedInRows(account: AccountState.SignedIn) {
    val context = LocalContext.current
    val online = (context.applicationContext as TrackProApp).online
    val scope = rememberCoroutineScope()
    val sharing by online.settings.sharingEnabled.collectAsState()
    val lastSync by online.settings.lastSync.collectAsState()
    val syncing by remember { SyncScheduler.isSyncing(context) }.collectAsState(initial = false)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        ValueRow(
            label = "Account",
            value = "${account.displayName} · ${account.email}",
            action = "Sign out",
            actionSelected = false,
            onAction = {
                scope.launch {
                    SyncScheduler.cancelAll(context)
                    online.auth.signOut()
                }
            }
        )

        Column {
            ValueRow(
                label = "Leaderboards",
                value = if (sharing) "Sharing your laps" else "Not sharing",
                action = if (sharing) "Stop sharing" else "Share laps",
                actionSelected = sharing,
                onAction = {
                    online.settings.setSharingEnabled(!sharing)
                    SyncScheduler.syncSoon(context)
                }
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                // Says exactly what leaves the phone, so the choice is an informed one.
                "Shared: your name, lap and sector times, session date, vehicle make, model and year, " +
                    "and which GPS was used. Only sessions recorded from this version on, on premade or " +
                    "published tracks. Stop sharing takes them back down.",
                style = TrackProType.body,
                color = TrackProTheme.colors.textMuted
            )
        }

        ValueRow(
            label = "Last sync",
            value = describe(lastSync),
            valueColor = if (lastSync?.problem != null && lastSync?.offline != true) {
                TrackProTheme.colors.deltaBad
            } else {
                TrackProTheme.colors.textMuted
            },
            action = if (syncing) "Syncing…" else "Sync now",
            actionSelected = false,
            onAction = { if (!syncing) SyncScheduler.syncSoon(context) }
        )
    }
}

private fun describe(report: SyncReport?): String {
    if (report == null) return "Not synced yet"
    val at = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(report.finishedAt))
    if (report.offline) return "Offline at $at · will retry"

    val parts = buildList {
        if (report.uploaded > 0) add("${report.uploaded} posted")
        if (report.withdrawn > 0) add("${report.withdrawn} withdrawn")
        if (report.unchanged > 0) add("${report.unchanged} up to date")
        if (report.failed > 0) add("${report.failed} failed")
    }
    val summary = parts.ifEmpty { listOf("Nothing to post") }.joinToString(" · ")
    return listOfNotNull("$summary · $at", report.problem).joinToString("\n")
}

@Composable
private fun ValueRow(
    label: String,
    value: String,
    action: String,
    actionSelected: Boolean,
    onAction: () -> Unit,
    valueColor: androidx.compose.ui.graphics.Color = TrackProTheme.colors.textMuted,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Placard(label)
            Spacer(Modifier.height(2.dp))
            Text(value, style = TrackProType.body, color = valueColor)
        }
        Spacer(Modifier.width(Spacing.sm))
        ToggleChip(text = action, selected = actionSelected, onClick = onAction)
    }
}

@Composable
private fun Placard(text: String) {
    Text(text.uppercase(), style = TrackProType.label, color = TrackProTheme.colors.textPrimary)
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TrackProTheme.colors.textPrimary,
    unfocusedTextColor = TrackProTheme.colors.textPrimary,
    focusedBorderColor = TrackProTheme.colors.accent,
    unfocusedBorderColor = TrackProTheme.colors.sectorLine
)
