package com.example.trackpro.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.trackpro.R
import java.util.Locale

/*
 * Values that are stored in English - the car options from vehicle_options.json, a track's
 * type and country - shown in the reader's language. Only the display changes: the stored
 * value stays what it is, so syncing, filtering and older data are unaffected. A value with
 * no translation (a custom entry, a code like RWD) is shown as stored.
 */

private val specLabels: Map<String, Int> = mapOf(
    "Petrol" to R.string.spec_petrol,
    "Diesel" to R.string.spec_diesel,
    "Electric" to R.string.spec_electric,
    "Hybrid" to R.string.spec_hybrid,
    "Ethanol" to R.string.spec_ethanol,
    "Hydrogen" to R.string.spec_hydrogen,
    "Methanol" to R.string.spec_methanol,
    "Street" to R.string.spec_street,
    "Semi-Slick" to R.string.spec_semi_slick,
    "Slick" to R.string.spec_slick,
    "All-Weather" to R.string.spec_all_weather,
    "Soft Compound" to R.string.spec_soft_compound,
    "Medium Compound" to R.string.spec_medium_compound,
    "Hard Compound" to R.string.spec_hard_compound,
    "Wet Tires" to R.string.spec_wet_tires,
    "Drag Radials" to R.string.spec_drag_radials,
    "Off-Road" to R.string.spec_off_road,
    "Manual" to R.string.spec_manual,
    "Automatic" to R.string.spec_automatic,
    "Sequential" to R.string.spec_sequential,
    "Dual-Clutch" to R.string.spec_dual_clutch,
    "CVT" to R.string.spec_cvt,
    "Direct-Drive" to R.string.spec_direct_drive,
    "Independent" to R.string.spec_independent,
    "Double Wishbone" to R.string.spec_double_wishbone,
    "Pushrod" to R.string.spec_pushrod,
    "Multilink" to R.string.spec_multilink,
    "Trailing Arm" to R.string.spec_trailing_arm,
    "Torsion Beam" to R.string.spec_torsion_beam,
    "Active Suspension" to R.string.spec_active_suspension,
    "Hydropneumatic" to R.string.spec_hydropneumatic,
    "Solid Axle" to R.string.spec_solid_axle,
    "Inline-3" to R.string.spec_inline_3,
    "Inline-4" to R.string.spec_inline_4,
    "Inline-5" to R.string.spec_inline_5,
    "Inline-6" to R.string.spec_inline_6,
    "Rotary-2" to R.string.spec_rotary_2,
)

/** A car option (fuel, tyres, gearbox, suspension, engine) in the reader's language. */
@Composable
fun specLabel(value: String): String = specLabels[value]?.let { stringResource(it) } ?: value

/** Every known option, translated, for lists that cannot call [specLabel] per item. */
@Composable
fun specLabelMap(): Map<String, String> = specLabels.mapValues { (_, id) -> stringResource(id) }

@Composable
fun trackTypeLabel(type: String): String = when (type) {
    "Circuit" -> stringResource(R.string.track_type_circuit)
    "Sprint" -> stringResource(R.string.track_type_sprint)
    else -> type
}

/** A country stored by its English name, in the current language: "Hungary" -> "Magyarország". */
fun localizedCountry(name: String): String {
    if (name.isBlank()) return name
    val code = Locale.getISOCountries().firstOrNull { Locale("", it).getDisplayCountry(Locale.ENGLISH).equals(name, ignoreCase = true) }
    return code?.let { Locale("", it).displayCountry } ?: name
}
