package com.example.trackpro.screens

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.TrackProApp
import com.example.trackpro.components.AppCard
import com.example.trackpro.components.ScreenScaffold
import com.example.trackpro.components.isScrolledUnderChrome
import com.example.trackpro.components.SectionLabel
import com.example.trackpro.components.ToggleChip
import com.example.trackpro.extrasForUI.AppDropdownField
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.models.GpsProviderType
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
    val useDarkTheme by app.useDarkTheme.collectAsState()
    val useMetric by app.useMetricUnits.collectAsState()

    val scrollState = rememberScrollState()
    val scrolled by scrollState.isScrolledUnderChrome()

    ScreenScaffold(
        title = "Settings",
        accent = TrackProTheme.colors.textMuted,
        onBack = onBack,
        contentScrolled = scrolled
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(
                    top = contentPadding.calculateTopPadding() + Spacing.md,
                    start = Spacing.md,
                    end = Spacing.md,
                    bottom = Spacing.md
                ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // --- Section: Hardware & GPS ---
            SectionLabel("Hardware & Sensors")
            AppCard {
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
            SectionLabel("Appearance")
            AppCard {
                SettingsToggleRow(
                    label = "Theme",
                    valueText = if (useDarkTheme) "Dark" else "Light",
                    valueColor = TrackProTheme.colors.textMuted,
                    buttonText = if (useDarkTheme) "Use Light" else "Use Dark",
                    isActive = true,
                    onClick = { app.setDarkTheme(!useDarkTheme) }
                )
            }

            // --- Section: Units ---
            SectionLabel("Units")
            AppCard {
                SettingsToggleRow(
                    label = "Speed & Distance",
                    valueText = if (useMetric) "Metric (km/h, km)" else "Imperial (mph, mi)",
                    valueColor = TrackProTheme.colors.textMuted,
                    buttonText = if (useMetric) "Use mph" else "Use km/h",
                    isActive = true,
                    onClick = { app.setMetricUnits(!useMetric) }
                )
            }

            // --- Section: System ---
            SectionLabel("Application")
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SettingsInfoRow(label = "App Version", value = "1.0.4-PRO")
                    SettingsInfoRow(label = "Database Status", value = "Connected")
                    SettingsInfoRow(label = "Map & Track Data", value = "© OpenStreetMap contributors")
                }
            }

            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun GpsSourceRow(selected: GpsProviderType, onSelect: (GpsProviderType) -> Unit) {
    Column {
        Text("GPS SOURCE", style = TrackProType.label, color = TrackProTheme.colors.textPrimary)
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

private fun gpsSourceLabel(source: GpsProviderType): String = when (source) {
    GpsProviderType.WIFI -> "WiFi"
    GpsProviderType.BLUETOOTH -> "Bluetooth"
    GpsProviderType.PHONE_GPS -> "Phone"
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
        ?: "Select a paired device"
    AppDropdownField(
        label = "Bluetooth Device",
        items = devices,
        selectedLabel = selectedLabel,
        itemLabel = { it.name ?: it.address },
        onSelect = onSelect,
        emptyMessage = if (hasPermission) {
            "No paired devices — pair the ESP32 in Android Bluetooth settings first"
        } else {
            "Bluetooth permission needed — tap WiFi then Bluetooth again to re-prompt"
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
                Text("ESP TARGET", style = TrackProType.label, color = TrackProTheme.colors.textPrimary)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (useTestServer) "Test Simulator" else "Real Device (192.168.4.1)",
                    style = TrackProType.body,
                    color = TrackProTheme.colors.textMuted
                )
            }
            ToggleChip(
                text = if (useTestServer) "Use Real Device" else "Use Test Simulator",
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
                label = { Text("Simulator IP Address", color = TrackProTheme.colors.textMuted) },
                placeholder = { Text("e.g. 192.168.1.50", color = TrackProTheme.colors.textFaint) },
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
            Text("GPS RATE", style = TrackProType.label, color = TrackProTheme.colors.textPrimary)
            if (confirmedHz != null) {
                Text(
                    text = if (confirmedHz == selectedHz) "· confirmed" else "· device at ${confirmedHz}Hz",
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
            Text(label.uppercase(), style = TrackProType.label, color = TrackProTheme.colors.textPrimary)
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
