package com.example.trackpro.screens.vehicleScreens

import com.example.trackpro.managerClasses.JsonReader
import com.example.trackpro.online.VehicleSpecVariant
import java.util.Locale

/**
 * What a catalogue spec sheet puts into the car form. Null means "leave the field as it is":
 * a sheet never blanks something the driver typed, and every value stays editable afterwards.
 * Option fields only ever hold a value from the form's own option lists, so a dropdown never
 * shows something it cannot select.
 */
data class SpecPrefill(
    val manufacturer: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val horsepower: Int? = null,
    val torqueNm: Int? = null,
    val weightKg: Double? = null,
    val topSpeedKmh: Double? = null,
    val zeroToHundredSeconds: Double? = null,
    val fuelCapacityLitres: Double? = null,
    val engineType: String? = null,
    val drivetrain: String? = null,
    val fuelType: String? = null,
    val transmission: String? = null,
    val suspensionType: String? = null,
) {
    companion object {
        fun from(
            variant: VehicleSpecVariant,
            options: JsonReader.VehicleOptions,
            motorcycle: Boolean,
            year: Int? = null,
        ): SpecPrefill = SpecPrefill(
            manufacturer = variant.make.takeIf { it.isNotBlank() },
            model = variant.model.takeIf { it.isNotBlank() },
            year = year ?: variant.yearFrom,
            horsepower = variant.powerHp?.takeIf { it > 0 },
            torqueNm = variant.torqueNm?.takeIf { it > 0 },
            weightKg = variant.weightKg?.takeIf { it > 0 },
            topSpeedKmh = variant.topSpeedKmh?.takeIf { it > 0 },
            zeroToHundredSeconds = variant.zeroToHundredSeconds?.takeIf { it > 0 },
            fuelCapacityLitres = variant.fuelCapacityLitres?.takeIf { it > 0 },
            engineType = engineType(variant.engine, variant.cylinders, motorcycle).within(options.engineTypes),
            drivetrain = variant.drivetrain.within(options.drivetrains),
            fuelType = variant.fuel.within(options.fuelTypes),
            transmission = variant.transmission.within(options.transmissions),
            suspensionType = suspensionType(variant.frontSuspension, variant.rearSuspension, motorcycle)
                .within(options.suspensionTypes),
        )

        /**
         * The engine option for a sheet. Read from the description first, since it names the
         * layout ("V6", "boxer"). A bare cylinder count only decides it where the layout is
         * near-certain: 3 to 5 are inline, 8 and up are vees. A six stays unset - a Lexus IS 200
         * is an inline-6, an Audi A4 2.4 a V6, and guessing either way is worse than asking.
         */
        fun engineType(engine: String?, cylinders: Int?, motorcycle: Boolean): String? {
            val text = engine.orEmpty().lowercase(Locale.ROOT)
            if (motorcycle) {
                // Bike sheets lead with the layout: "In-line four, four-stroke", "V2, four-stroke".
                val layout = text.substringBefore(',')
                return when {
                    "boxer" in layout || "flat" in layout -> "Boxer-2"
                    "v4" in layout -> "V4"
                    "v2" in layout || "v-twin" in layout || "v twin" in layout -> "V-Twin"
                    "single" in layout -> "Single"
                    "twin" in layout -> "Inline-2"
                    "three" in layout || "triple" in layout -> "Inline-3"
                    "four" in layout -> "Inline-4"
                    "six" in layout -> "Inline-6"
                    else -> null
                }
            }
            Regex("""\bv(6|8|10|12)\b""").find(text)?.let { return "V${it.groupValues[1]}" }
            return when {
                "rotary" in text || "wankel" in text -> "Rotary-2"
                "vr6" in text -> "VR-6"
                "boxer" in text || "flat" in text -> when (cylinders) {
                    4 -> "Boxer-4"
                    6 -> "Boxer-6"
                    else -> null
                }
                "inline" in text || "in-line" in text || "straight" in text ->
                    cylinders?.takeIf { it in 3..6 }?.let { "Inline-$it" }
                else -> when (cylinders) {
                    3, 4, 5 -> "Inline-$cylinders"
                    8, 10, 12 -> "V$cylinders"
                    else -> null
                }
            }
        }

        /** The form keeps one suspension type, so the most telling layout on either axle wins. */
        fun suspensionType(front: String?, rear: String?, motorcycle: Boolean): String? {
            val text = listOfNotNull(front, rear).joinToString(" ").lowercase(Locale.ROOT)
            if (text.isBlank()) return null
            if (motorcycle) {
                val fork = front.orEmpty().lowercase(Locale.ROOT)
                return when {
                    "upside" in fork || "usd" in fork || "inverted" in fork -> "Upside-Down Fork"
                    "fork" in fork -> "Telescopic Fork"
                    else -> null
                }
            }
            return when {
                "hydropneumatic" in text || "hydractive" in text -> "Hydropneumatic"
                "active" in text || "adaptive" in text || "air suspension" in text -> "Active Suspension"
                "double wishbone" in text || "double-wishbone" in text -> "Double Wishbone"
                "pushrod" in text -> "Pushrod"
                "multi-link" in text || "multilink" in text || "multi link" in text -> "Multilink"
                "torsion" in text -> "Torsion Beam"
                "trailing arm" in text -> "Trailing Arm"
                "solid axle" in text || "rigid axle" in text || "live axle" in text -> "Solid Axle"
                "independent" in text || "mcpherson" in text || "macpherson" in text -> "Independent"
                else -> null
            }
        }

        private fun String?.within(options: List<String>): String? =
            this?.let { value -> options.firstOrNull { it.equals(value, ignoreCase = true) } }
    }
}
