package com.example.trackpro.screens

import com.example.trackpro.R
import androidx.compose.ui.res.stringResource
import com.example.trackpro.managerClasses.timeAttackManagers.DeltaReference
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.TrackProApp
import com.example.trackpro.managerClasses.utilities.AppLanguage
import com.example.trackpro.managerClasses.utilities.findActivity
import com.example.trackpro.components.DashGroup
import com.example.trackpro.theme.markingDim
import com.example.trackpro.components.ScreenScaffold
import com.example.trackpro.components.isScrolledUnderChrome
import com.example.trackpro.components.ToggleChip
import com.example.trackpro.extrasForUI.AppDropdownField
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.models.GpsProviderType
import com.example.trackpro.online.ui.OnlineSettingsGroup
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.Spacing
import com.example.trackpro.theme.TrackProType

@Composable
fun SettingsScreen(onBack: () -> Unit, onRequestBluetoothPermission: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as TrackProApp
    val gpsSource by app.gpsSource.collectAsState()
    val selectedRateHz by app.selectedRateHz.collectAsState()
    val confirmedRateHz by app.gpsManager.confirmedRateHz.collectAsState(initial = null)
    val selectedBtDeviceMac by app.selectedBtDeviceMac.collectAsState()
    val useTestServer by app.useTestServer.collectAsState()
    val testServerAddress by app.testServerAddress.collectAsState()
    val sunlight by app.useSunlightContrast.collectAsState()
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "—"
    }
    val useMetric by app.useMetricUnits.collectAsState()
    val deltaReference by app.deltaReference.collectAsState()
    val language by app.appLanguage.collectAsState()

    val scrollState = rememberScrollState()
    val scrolled by scrollState.isScrolledUnderChrome()

    ScreenScaffold(
        title = stringResource(R.string.settings_title),
        accent = TrackProTheme.colors.textMuted,
        onBack = onBack,
        contentScrolled = scrolled
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(top = contentPadding.calculateTopPadding()),
        ) {
            // --- Section: Language ---
            DashGroup(stringResource(R.string.settings_language)) {
                LanguageRow(
                    selected = language,
                    onSelect = { tag ->
                        if (tag != language) {
                            app.setAppLanguage(tag)
                            // Every string on screen was read at composition; recreating is
                            // what re-reads them. The back stack is restored on the way.
                            context.findActivity()?.recreate()
                        }
                    }
                )
            }

            // --- Section: Hardware & GPS ---
            DashGroup(stringResource(R.string.settings_hardware)) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    GpsSourceRow(
                        selected = gpsSource,
                        onSelect = { source ->
                            app.setGpsSource(source)
                            if (source == GpsProviderType.BLUETOOTH) onRequestBluetoothPermission()
                        }
                    )

                    if (gpsSource == GpsProviderType.BLUETOOTH) {
                        BluetoothDeviceRow(
                            devices = app.bluetoothClassicClient.getBondedDevices(),
                            selectedMac = selectedBtDeviceMac,
                            hasPermission = app.bluetoothClassicClient.hasBluetoothPermission(),
                            onSelect = { device -> app.setSelectedBtDevice(device.address) }
                        )
                    }

                    if (gpsSource == GpsProviderType.WIFI) {
                        EspTargetRow(
                            useTestServer = useTestServer,
                            testServerAddress = testServerAddress,
                            onToggle = { app.setUseTestServer(it) },
                            onAddressChange = { app.setTestServerAddress(it) }
                        )
                    }

                    if (gpsSource != GpsProviderType.PHONE_GPS) {
                        GpsRateRow(
                            selectedHz = selectedRateHz,
                            confirmedHz = confirmedRateHz,
                            onSelect = { hz -> app.setRateHz(hz) }
                        )
                    }
                }
            }

            // --- Section: Appearance ---
            DashGroup(stringResource(R.string.settings_appearance)) {
                SettingsToggleRow(
                    label = stringResource(R.string.settings_sunlight),
                    valueText = if (sunlight) stringResource(R.string.settings_sunlight_on) else stringResource(R.string.settings_sunlight_off),
                    valueColor = TrackProTheme.colors.textMuted,
                    buttonText = if (sunlight) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                    isActive = sunlight,
                    onClick = { app.setSunlightContrast(!sunlight) }
                )
            }

            // --- Section: Units ---
            DashGroup(stringResource(R.string.settings_units)) {
                SettingsToggleRow(
                    label = stringResource(R.string.settings_speed_distance),
                    valueText = if (useMetric) stringResource(R.string.settings_metric) else stringResource(R.string.settings_imperial),
                    valueColor = TrackProTheme.colors.textMuted,
                    buttonText = if (useMetric) stringResource(R.string.settings_use_mph) else stringResource(R.string.settings_use_kmh),
                    isActive = true,
                    onClick = { app.setMetricUnits(!useMetric) }
                )
            }

            // --- Section: Timing ---
            DashGroup(stringResource(R.string.settings_timing)) {
                SettingsToggleRow(
                    label = stringResource(R.string.settings_live_delta),
                    valueText = if (deltaReference == DeltaReference.TRACK_BEST) {
                        stringResource(R.string.settings_delta_track)
                    } else {
                        stringResource(R.string.settings_delta_session)
                    },
                    valueColor = TrackProTheme.colors.textMuted,
                    buttonText = if (deltaReference == DeltaReference.TRACK_BEST) {
                        stringResource(R.string.settings_use_session_best)
                    } else {
                        stringResource(R.string.settings_use_track_best)
                    },
                    isActive = true,
                    onClick = {
                        app.setDeltaReference(
                            if (deltaReference == DeltaReference.TRACK_BEST) DeltaReference.SESSION_BEST
                            else DeltaReference.TRACK_BEST
                        )
                    }
                )
            }

            OnlineSettingsGroup()

            // --- Section: System ---
            DashGroup(stringResource(R.string.settings_application)) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SettingsInfoRow(label = stringResource(R.string.settings_version), value = appVersion)
                    SettingsInfoRow(label = stringResource(R.string.settings_map_data), value = stringResource(R.string.settings_osm))
                }
            }

            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun LanguageRow(selected: String, onSelect: (String) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            // Each language names itself, so it can be found from either.
            listOf(
                AppLanguage.SYSTEM to stringResource(R.string.settings_language_system),
                AppLanguage.ENGLISH to "English",
                AppLanguage.HUNGARIAN to "Magyar",
            ).forEach { (tag, label) ->
                ToggleChip(
                    text = label,
                    selected = selected == tag,
                    onClick = { onSelect(tag) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (selected == AppLanguage.SYSTEM) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_language_system_hint),
                style = TrackProType.label,
                color = TrackProTheme.colors.textMuted
            )
        }
    }
}

@Composable
private fun GpsSourceRow(selected: GpsProviderType, onSelect: (GpsProviderType) -> Unit) {
    Column {
        Text(stringResource(R.string.settings_gps_source), style = TrackProType.label, color = TrackProTheme.colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            GpsProviderType.values().forEach { source ->
                ToggleChip(
                    text = gpsSourceLabel(source),
                    selected = selected == source,
                    onClick = { onSelect(source) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun gpsSourceLabel(source: GpsProviderType): String = when (source) {
    GpsProviderType.WIFI -> stringResource(R.string.gps_source_wifi)
    GpsProviderType.BLUETOOTH -> stringResource(R.string.gps_source_bluetooth)
    GpsProviderType.PHONE_GPS -> stringResource(R.string.gps_source_phone)
}

@SuppressLint("MissingPermission")
@Composable
private fun BluetoothDeviceRow(
    devices: List<BluetoothDevice>,
    selectedMac: String?,
    hasPermission: Boolean,
    onSelect: (BluetoothDevice) -> Unit
) {
    val selectedLabel = devices.find { it.address == selectedMac }?.let { it.name ?: it.address }
        ?: stringResource(R.string.settings_select_paired)
    AppDropdownField(
        label = stringResource(R.string.settings_bt_device),
        items = devices,
        selectedLabel = selectedLabel,
        itemLabel = { it.name ?: it.address },
        onSelect = onSelect,
        emptyMessage = if (hasPermission) {
            stringResource(R.string.settings_no_paired)
        } else {
            stringResource(R.string.settings_bt_permission)
        }
    )
}

@Composable
private fun EspTargetRow(
    useTestServer: Boolean,
    testServerAddress: String,
    onToggle: (Boolean) -> Unit,
    onAddressChange: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stringResource(R.string.settings_esp_target), style = TrackProType.label, color = TrackProTheme.colors.textPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (useTestServer) stringResource(R.string.settings_test_sim) else stringResource(R.string.settings_real_device),
                    style = TrackProType.body,
                    color = TrackProTheme.colors.textMuted
                )
            }
            ToggleChip(
                text = if (useTestServer) stringResource(R.string.settings_use_real) else stringResource(R.string.settings_use_sim),
                selected = useTestServer,
                onClick = { onToggle(!useTestServer) },
                accent = TrackProTheme.colors.accent
            )
        }

        if (useTestServer) {
            Spacer(Modifier.height(Spacing.sm))
            OutlinedTextField(
                value = testServerAddress,
                onValueChange = onAddressChange,
                label = { Text(stringResource(R.string.settings_sim_ip), color = TrackProTheme.colors.textMuted) },
                placeholder = { Text(stringResource(R.string.settings_sim_ip_hint), color = TrackProTheme.colors.markingDim) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TrackProTheme.colors.textPrimary,
                    unfocusedTextColor = TrackProTheme.colors.textPrimary,
                    focusedBorderColor = TrackProTheme.colors.accent,
                    unfocusedBorderColor = TrackProTheme.colors.sectorLine
                )
            )
        }
    }
}

@Composable
private fun GpsRateRow(selectedHz: Int, confirmedHz: Int?, onSelect: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.settings_gps_rate), style = TrackProType.label, color = TrackProTheme.colors.textPrimary)
            if (confirmedHz != null) {
                Text(
                    text = if (confirmedHz == selectedHz) stringResource(R.string.settings_rate_confirmed) else stringResource(R.string.settings_rate_device, confirmedHz),
                    style = TrackProType.body.atSize(10.sp),
                    // A mismatch between requested and confirmed rate is a real problem
                    // worth flagging, so this is one of the few places color is earned.
                    color = if (confirmedHz == selectedHz) TrackProTheme.colors.deltaGood
                            else TrackProTheme.colors.deltaBad
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            listOf(5, 10, 20, 25).forEach { hz ->
                ToggleChip(
                    text = "$hz Hz",
                    selected = selectedHz == hz,
                    onClick = { onSelect(hz) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * A labeled setting with a value line on the left and a single toggle action on the
 * right. The three toggles on this screen (GPS source, theme, units) all used to
 * hand-roll this same Row+Button block.
 */
@Composable
private fun SettingsToggleRow(
    label: String,
    valueText: String,
    valueColor: Color,
    buttonText: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(label, style = TrackProType.label, color = TrackProTheme.colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(valueText, style = TrackProType.body, color = valueColor)
        }
        ToggleChip(
            text = buttonText,
            selected = isActive,
            onClick = onClick,
            accent = TrackProTheme.colors.accent
        )
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = TrackProType.body, color = TrackProTheme.colors.textMuted)
        Text(value, style = TrackProType.body, color = TrackProTheme.colors.textPrimary)
    }
}
