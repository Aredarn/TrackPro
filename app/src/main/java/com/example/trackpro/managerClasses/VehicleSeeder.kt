package com.example.trackpro.managerClasses

import android.util.Log
import com.example.trackpro.dataClasses.VehicleInformationData

/**
 * Seeds a small set of default vehicles so the app is never empty, even on a clean
 * install. Runs on every app start (mirrors TrackSeeder) and dedupes by
 * manufacturer+model+year, so it's always safe to re-run and re-adds a default
 * vehicle if it was ever deleted.
 */
object VehicleSeeder {
    private const val TAG = "VehicleSeeder"

    private val DEFAULT_VEHICLES = listOf(
        VehicleInformationData(
            manufacturer = "Lexus",
            model = "IS200",
            year = 1999,
            engineType = "Inline-6",
            horsepower = 155,
            torque = 200,
            weight = 1420.0,
            topSpeed = 205.0,
            acceleration = 9.9,
            drivetrain = "RWD",
            fuelType = "Petrol",
            tireType = "All-Weather",
            fuelCapacity = 65.0,
            transmission = "Automatic",
            suspensionType = "Double Wishbone"
        )
    )

    suspend fun syncDefaultVehicles(database: ESPDatabase) {
        try {
            val existing = database.vehicleInformationDAO().getAllVehicleSignatures().toSet()
            val missing = DEFAULT_VEHICLES.filter { "${it.manufacturer} ${it.model} ${it.year}" !in existing }

            if (missing.isEmpty()) {
                Log.d(TAG, "All ${DEFAULT_VEHICLES.size} default vehicles already present, nothing to seed")
                return
            }

            for (vehicle in missing) {
                database.vehicleInformationDAO().insertVehicle(vehicle)
                Log.d(TAG, "Seeded default vehicle: ${vehicle.manufacturer} ${vehicle.model} ${vehicle.year}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing default vehicles", e)
        }
    }
}
