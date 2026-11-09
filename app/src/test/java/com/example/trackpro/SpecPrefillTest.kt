package com.example.trackpro

import com.example.trackpro.managerClasses.JsonReader
import com.example.trackpro.online.VehicleSpecVariant
import com.example.trackpro.screens.vehicleScreens.SpecPrefill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The user's own garage makes good cases: an old Lexus, an Audi, a Skoda and a Yamaha. */
class SpecPrefillTest {

    /** Mirrors res/raw/vehicle_options.json. */
    private val options = JsonReader.VehicleOptions(
        engineTypes = listOf(
            "Single", "Inline-2", "V-Twin", "Boxer-2", "Inline-3", "Inline-4", "Inline-5", "Inline-6",
            "Rotary-2", "Boxer-4", "Boxer-6", "VR-6", "V4", "V6", "V8", "V10", "V12"
        ),
        drivetrains = listOf("FWD", "RWD", "AWD", "4WD", "XWD"),
        fuelTypes = listOf("Petrol", "Diesel", "Electric", "Hybrid", "Ethanol", "Hydrogen", "Methanol"),
        tireTypes = listOf("Street"),
        transmissions = listOf("Manual", "Automatic", "Sequential", "Dual-Clutch", "CVT", "Direct-Drive"),
        suspensionTypes = listOf(
            "Independent", "Double Wishbone", "Pushrod", "Multilink", "Trailing Arm", "Torsion Beam",
            "Active Suspension", "Hydropneumatic", "Solid Axle", "Telescopic Fork", "Upside-Down Fork"
        ),
    )

    @Test
    fun `a full car sheet fills every field it knows`() {
        val octavia = VehicleSpecVariant(
            name = "1.9 TDI (105 hp)", make = "Skoda", model = "Octavia", generation = "II (1Z)",
            yearFrom = 2004, yearTo = 2008, engine = "1.9 turbo diesel", cylinders = 4,
            powerHp = 105, torqueNm = 250, weightKg = 1320.0, topSpeedKmh = 191.0, zeroToHundredSeconds = 11.8,
            fuelCapacityLitres = 55.0, drivetrain = "FWD", fuel = "Diesel", transmission = "Manual",
            frontSuspension = "McPherson strut", rearSuspension = "Multi-link independent",
        )

        val fill = SpecPrefill.from(octavia, options, motorcycle = false, year = 2006)

        assertEquals(2006, fill.year)
        assertEquals(105, fill.horsepower)
        assertEquals(250, fill.torqueNm)
        assertEquals(191.0, fill.topSpeedKmh!!, 0.0)
        assertEquals("Inline-4", fill.engineType)
        assertEquals("FWD", fill.drivetrain)
        assertEquals("Diesel", fill.fuelType)
        assertEquals("Manual", fill.transmission)
        assertEquals("Multilink", fill.suspensionType)
    }

    @Test
    fun `a bare six cylinder count leaves the engine for the driver`() {
        // The IS 200 is an inline six; an A4 2.4 is a V6. Neither can be told from "6".
        assertNull(SpecPrefill.engineType("2.0 petrol", 6, motorcycle = false))
        assertEquals("Inline-6", SpecPrefill.engineType("2.0 inline-6 petrol", 6, motorcycle = false))
        assertEquals("V6", SpecPrefill.engineType("2.4 V6", 6, motorcycle = false))
        assertEquals("V8", SpecPrefill.engineType(null, 8, motorcycle = false))
    }

    @Test
    fun `a partial sheet fills only what it carries and nothing it cannot select`() {
        val a4 = VehicleSpecVariant(
            name = "B6", make = "Audi", model = "A4", generation = "B6", yearFrom = 2004,
            fuel = "LPG", transmission = "Manual", partial = true,
        )

        val fill = SpecPrefill.from(a4, options, motorcycle = false)

        assertEquals("Manual", fill.transmission)
        assertNull(fill.fuelType) // LPG is not one of the form's fuels
        assertNull(fill.horsepower)
        assertNull(fill.engineType)
        assertNull(fill.drivetrain)
    }

    @Test
    fun `a bike sheet reads its layout and fork`() {
        val fz8 = VehicleSpecVariant(
            name = "FZ8", make = "Yamaha", model = "FZ8", yearFrom = 2012,
            engine = "In-line four, four-stroke", cylinders = 4, powerHp = 106, torqueNm = 82,
            weightKg = 211.0, fuelCapacityLitres = 17.0, drivetrain = "RWD", fuel = "Petrol",
            transmission = "Sequential", frontSuspension = "43 mm upside-down telescopic fork",
            rearSuspension = "Link type monocross",
        )

        val fill = SpecPrefill.from(fz8, options, motorcycle = true)

        assertEquals("Inline-4", fill.engineType)
        assertEquals("Upside-Down Fork", fill.suspensionType)
        assertEquals("Sequential", fill.transmission)
        assertEquals(17.0, fill.fuelCapacityLitres!!, 0.0)
        assertEquals("V-Twin", SpecPrefill.engineType("V2, four-stroke", 2, motorcycle = true))
        assertEquals("Inline-2", SpecPrefill.engineType("Twin, four-stroke", 2, motorcycle = true))
        assertEquals("Telescopic Fork", SpecPrefill.suspensionType("41mm telescopic fork", null, motorcycle = true))
    }
}
