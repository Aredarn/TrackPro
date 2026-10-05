package com.example.trackpro.screens.profile

import com.example.trackpro.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.trackpro.online.MessageText
import com.example.trackpro.theme.Spacing
import com.example.trackpro.components.paddockCard
import com.example.trackpro.components.SectionTitle
import com.example.trackpro.components.PaddockCard
import com.example.trackpro.components.IconCircle
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.trackpro.TrackProApp
import com.example.trackpro.components.Haptic
import com.example.trackpro.components.PhotoFrame
import com.example.trackpro.components.PrimaryButton
import com.example.trackpro.components.ScreenScaffold
import com.example.trackpro.components.ToggleChip
import com.example.trackpro.components.initialsOf
import com.example.trackpro.components.pressable
import com.example.trackpro.components.rememberPhotoPicker
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.online.AccountState
import com.example.trackpro.online.ApiException
import com.example.trackpro.online.NetworkException
import com.example.trackpro.online.ui.AccountSignInForm
import com.example.trackpro.online.ui.ServerRow
import com.example.trackpro.online.ui.fieldColors
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.field
import com.example.trackpro.theme.marking
import com.example.trackpro.theme.markingDim
import com.example.trackpro.theme.panel
import kotlinx.coroutines.launch

/**
 * Sign in or create an account, reached from the Profile tab and the Garage's backup strip.
 * Leaves by itself once signed in, back to wherever the driver came from.
 */
@Composable
fun AccountScreen(navController: NavController) {
    val context = LocalContext.current
    val online = (context.applicationContext as TrackProApp).online
    val account by online.auth.state.collectAsState()
    val serverUrl by online.settings.serverUrl.collectAsState()
    var editingServer by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(title = stringResource(R.string.profile_account), onBack = { navController.popBackStack() }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TrackProTheme.colors.panel)
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            PaddockCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconCircle(Icons.Filled.CloudDone, size = 48.dp)
                    Spacer(Modifier.width(Spacing.md))
                    Column {
                        Text("TrackBoard", style = TrackProType.titleLarge, color = TrackProTheme.colors.marking)
                        Text(stringResource(R.string.account_optional), style = TrackProType.label, color = TrackProTheme.colors.markingDim)
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Benefit(stringResource(R.string.account_benefit_backup))
                    Benefit(stringResource(R.string.account_benefit_rank))
                    Benefit(stringResource(R.string.account_benefit_optional))
                }
            }

            PaddockCard {
                AccountSignInForm(
                    notice = (account as? AccountState.SignedOut)?.notice,
                    serverSet = serverUrl.isNotBlank(),
                    onSignedIn = { navController.popBackStack() }
                )
            }

            // Most drivers never need to see the address; it is one tap away for those who do.
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.account_server, serverUrl.removePrefix("https://").removePrefix("http://").ifBlank { stringResource(R.string.account_not_set) }),
                    style = TrackProType.label,
                    color = TrackProTheme.colors.markingDim,
                    modifier = Modifier.weight(1f)
                )
                ToggleChip(text = if (editingServer) stringResource(R.string.common_done) else stringResource(R.string.rig_change), selected = false, onClick = { editingServer = !editingServer })
            }
            if (editingServer) {
                PaddockCard {
                    ServerRow(url = serverUrl, onChange = online.settings::setServerUrl)
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun Benefit(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = TrackProTheme.colors.accent,
            modifier = Modifier.padding(top = 2.dp).size(18.dp)
        )
        Spacer(Modifier.width(Spacing.md))
        Text(text, style = TrackProType.body, color = TrackProTheme.colors.textMuted)
    }
}

/** Name, photo, country and bio. Saved together; the photo applies the moment it is chosen. */
@Composable
fun EditProfileScreen(navController: NavController) {
    val context = LocalContext.current
    val online = (context.applicationContext as TrackProApp).online
    val scope = rememberCoroutineScope()
    val accountState by online.auth.state.collectAsState()
    val snapshotAll by online.profile.account.collectAsState()
    val snapshot = remember(snapshotAll, accountState) { online.profile.snapshotFor(accountState) }
    val profile = snapshot?.profile

    var name by rememberSaveable(profile?.displayName) { mutableStateOf(profile?.displayName ?: (accountState as? AccountState.SignedIn)?.displayName.orEmpty()) }
    var country by rememberSaveable(profile?.country) { mutableStateOf(profile?.country.orEmpty()) }
    var bio by rememberSaveable(profile?.bio) { mutableStateOf(profile?.bio.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var photoBusy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberPhotoPicker { uri ->
        photoBusy = true
        error = null
        scope.launch {
            try {
                online.profile.setAvatar(uri)
            } catch (e: ApiException) {
                error = MessageText.localize(e.message)
            } catch (e: NetworkException) {
                error = context.getString(R.string.account_photo_needs_connection, MessageText.localize(e.message) ?: "")
            } finally {
                photoBusy = false
            }
        }
    }

    ScreenScaffold(title = stringResource(R.string.profile_edit), onBack = { navController.popBackStack() }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TrackProTheme.colors.panel)
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding())
        ) {
            PaddockCard(modifier = Modifier.padding(horizontal = Spacing.gutter)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    PhotoFrame(
                        file = online.photos.file(snapshot?.avatarFile),
                        contentDescription = stringResource(R.string.profile_photo),
                        initials = initialsOf(name.ifBlank { stringResource(R.string.drive_driver) }),
                        shape = CircleShape,
                        modifier = Modifier.size(112.dp)
                    )
                    Text(
                        if (photoBusy) stringResource(R.string.account_uploading) else stringResource(R.string.profile_photo),
                        style = TrackProType.label,
                        color = TrackProTheme.colors.markingDim
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                        ToggleChip(text = stringResource(R.string.photo_take), selected = false, onClick = { if (!photoBusy) picker.fromCamera() }, modifier = Modifier.weight(1f))
                        ToggleChip(text = stringResource(R.string.account_choose_photo), selected = false, onClick = { if (!photoBusy) picker.fromGallery() }, modifier = Modifier.weight(1f))
                    }
                    if (snapshot?.avatarFile != null) {
                        Text(
                            stringResource(R.string.account_remove_photo),
                            style = TrackProType.label,
                            color = TrackProTheme.colors.danger,
                            modifier = Modifier
                                .pressable(
                                    onClick = {
                                        if (!photoBusy) scope.launch {
                                            photoBusy = true
                                            try {
                                                online.profile.removeAvatar()
                                            } catch (e: ApiException) {
                                                error = MessageText.localize(e.message)
                                            } catch (e: NetworkException) {
                                                error = context.getString(R.string.account_remove_photo_needs_connection, MessageText.localize(e.message) ?: "")
                                            } finally {
                                                photoBusy = false
                                            }
                                        }
                                    },
                                    scale = 0.96f
                                )
                                .heightIn(min = 48.dp)
                                .wrapContentHeight(Alignment.CenterVertically)
                        )
                    }
                }
            }

            SectionTitle(stringResource(R.string.account_details))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter)
                    .paddockCard()
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(100) },
                    label = { Text(stringResource(R.string.account_name_on_boards), color = TrackProTheme.colors.textMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it.take(60) },
                    label = { Text(stringResource(R.string.builder_country), color = TrackProTheme.colors.textMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(500) },
                    label = { Text(stringResource(R.string.account_about_you), color = TrackProTheme.colors.textMuted) },
                    supportingText = { Text("${bio.length} / 500", color = TrackProTheme.colors.textMuted) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                error?.let { Text(it, style = TrackProType.body, color = TrackProTheme.colors.deltaBad) }
                PrimaryButton(
                    text = if (busy) stringResource(R.string.account_saving) else stringResource(R.string.account_save),
                    enabled = !busy && name.trim().length >= 2,
                    haptic = Haptic.Confirm,
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                online.profile.update(displayName = name.trim(), bio = bio.trim(), country = country.trim())
                                navController.popBackStack()
                            } catch (e: ApiException) {
                                error = MessageText.localize(e.message)
                            } catch (e: NetworkException) {
                                error = context.getString(R.string.account_save_needs_connection, MessageText.localize(e.message) ?: "")
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
