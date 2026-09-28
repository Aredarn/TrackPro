package com.example.trackpro.online

import com.example.trackpro.dataClasses.RemoteLink
import com.example.trackpro.dataClasses.VehicleInformationData

/** Where a car stands against the driver's account, as the garage shows it. */
enum class VehicleSyncStatus(val label: String) {
    /** Signed out: the car exists on this phone, and that is all there is to say. */
    OnThisPhone("On this phone"),
    /** New or edited here, waiting for the next sync. */
    Pending("Waiting to back up"),
    Synced("Backed up"),
    /** The account refused it; [RemoteLink.lastError] says why. */
    Failed("Backup failed"),
    /** Removed from the account on another phone and kept here. */
    LocalOnly("Only on this phone"),
}

fun vehicleSyncStatus(
    vehicle: VehicleInformationData,
    link: RemoteLink?,
    signedIn: Boolean,
): VehicleSyncStatus {
    if (!signedIn) return VehicleSyncStatus.OnThisPhone
    if (link == null) return VehicleSyncStatus.Pending
    return when (link.uploadedHash) {
        GarageSync.REMOTE_GONE -> VehicleSyncStatus.LocalOnly
        GarageSync.PAIRED -> VehicleSyncStatus.Pending
        else -> when {
            link.lastError != null -> VehicleSyncStatus.Failed
            PayloadMapper.vehicle(vehicle)?.let(PayloadMapper::hash) != link.uploadedHash -> VehicleSyncStatus.Pending
            else -> VehicleSyncStatus.Synced
        }
    }
}
