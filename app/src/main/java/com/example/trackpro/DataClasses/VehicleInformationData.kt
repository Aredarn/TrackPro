package com.example.trackpro.DataClasses

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "vehicle_information_data")
data class VehicleInformationData(
    @PrimaryKey(autoGenerate = true)
    val vehicleId: Long = 0,
    val manufacturer: String,
    val model: String,
    val year: Int,
    val engineType: String, // e.g., "V8", "Turbocharged", "Electric"
    val horsepower: Int,
    val torque: Int?, // in Nm
    val weight: Double, // in kg
    val topSpeed: Double?, // in km/h
    val acceleration: Double?, // 0-100 km/h in seconds
    val drivetrain: String, // e.g., "RWD", "AWD", "FWD"
    val fuelType: String, // e.g., "Petrol", "Diesel", "Electric"
    val tireType: String, // e.g., "Slick", "All-Weather", "Soft Compound"
    val fuelCapacity: Double?, // in liters
    val transmission: String, // e.g., "Manual", "Automatic", "Sequential"
    val suspensionType: String?, // e.g., "Independent", "Double Wishbone"
)

