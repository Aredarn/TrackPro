package com.example.trackpro.online

import com.example.trackpro.R
import com.example.trackpro.dataClasses.RemoteLink
import com.example.trackpro.dataClasses.VehicleInformationData

/** Where a car stands against the driver's account, as the garage shows it. */
enum class VehicleSyncStatus(@androidx.annotation.StringRes val label: Int) {
    /** Signed out: the car exists on this phone, and that is all there is to say. */
    OnThisPhone(R.string.sync_on_phone),
    /** New or edited here, waiting for the next sync. */
    Pending(R.string.sync_pending),
    Synced(R.string.sync_synced),
    /** The account refused it; [RemoteLink.lastError] says why. */
    Failed(R.string.sync_failed),
    /** Removed from the account on another phone and kept here. */
    LocalOnly(R.string.sync_local_only),
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
