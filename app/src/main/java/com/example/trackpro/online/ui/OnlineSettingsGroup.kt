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
 * The Settings group for TrackBoard: only where the server is.
 *
 * The account itself — signing in, sharing laps, sync, export, deletion — lives on the
 * Profile tab, next to the career it belongs to. Settings used to hold all of it, which put a
 * driver's identity between the unit toggle and the GPS source.
 */
@Composable
fun OnlineSettingsGroup() {
    val context = LocalContext.current
    val online = (context.applicationContext as TrackProApp).online
    val account by online.auth.state.collectAsState()
    val serverUrl by online.settings.serverUrl.collectAsState()

    DashGroup("Online · TrackBoard") {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ServerRow(url = serverUrl, onChange = online.settings::setServerUrl)
            Text(
                when (val state = account) {
                    is AccountState.SignedIn -> "Signed in as ${state.displayName}. Your account, lap sharing and sync are on the Profile tab."
                    is AccountState.SignedOut -> "Optional. Sign in from the Profile tab to back up your garage and post laps to leaderboards. Recording never needs an account or a network."
                },
                style = TrackProType.body,
                color = TrackProTheme.colors.textMuted
            )
        }
    }
}

@Composable
internal fun ServerRow(url: String, onChange: (String) -> Unit) {
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

/** Sign in or create an account. [onSignedIn] runs after the tokens are stored. */
@Composable
internal fun AccountSignInForm(notice: String?, serverSet: Boolean, onSignedIn: () -> Unit = {}) {
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
            Text("Set a server first.", style = TrackProType.body, color = TrackProTheme.colors.textMuted)
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
                        onSignedIn()
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
private fun Placard(text: String) {
    Text(text, style = TrackProType.label, color = TrackProTheme.colors.textPrimary)
}

@Composable
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TrackProTheme.colors.textPrimary,
    unfocusedTextColor = TrackProTheme.colors.textPrimary,
    focusedBorderColor = TrackProTheme.colors.accent,
    unfocusedBorderColor = TrackProTheme.colors.sectorLine
)
