package com.example.trackpro.online

import android.util.Log
import kotlinx.serialization.Serializable

/** A make or model to pick. [key] is what narrows the next step; [name] is what to show. */
@Serializable
data class VehicleCatalogOption(val name: String, val key: String)

/**
 * One spec sheet from the server's vehicle catalogue. Everything but the names is optional:
 * the catalogue rarely knows all of it, and [partial] sheets (cars on the free plan) carry only
 * fuel, gearbox, drive and cylinder count. Drivetrain, fuel and transmission already use the
 * app's own option values ("RWD", "Petrol", "Manual").
 */
@Serializable
data class VehicleSpecVariant(
    val name: String,
    val make: String,
    val model: String,
    val generation: String? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val engine: String? = null,
    val cylinders: Int? = null,
    val displacementCc: Int? = null,
    val powerHp: Int? = null,
    val torqueNm: Int? = null,
    val weightKg: Double? = null,
    val topSpeedKmh: Double? = null,
    val zeroToHundredSeconds: Double? = null,
    val fuelCapacityLitres: Double? = null,
    val drivetrain: String? = null,
    val fuel: String? = null,
    val transmission: String? = null,
    val frontSuspension: String? = null,
    val rearSuspension: String? = null,
    val partial: Boolean = false,
)

/** The server's make/model/year lookup. A 503 means the server has no catalogue configured. */
interface TrackBoardVehicleSpecsApi {
    suspend fun carMakes(accessToken: String): List<VehicleCatalogOption>
    suspend fun carModels(accessToken: String, make: String): List<VehicleCatalogOption>
    suspend fun carYears(accessToken: String, make: String, model: String): List<Int>
    suspend fun carVariants(accessToken: String, make: String, model: String, year: Int): List<VehicleSpecVariant>
    suspend fun motorcycles(accessToken: String, make: String, model: String, year: Int?): List<VehicleSpecVariant>
}

/** What a catalogue lookup came back with. Anything but [Found] leaves the form to the driver. */
sealed interface CatalogResult<out T> {
    data class Found<T>(val value: T) : CatalogResult<T>
    /** Lookups go through TrackBoard, so they need an account. */
    data object SignedOut : CatalogResult<Nothing>
    /** The server has no catalogue, or its quota is spent. */
    data object Unavailable : CatalogResult<Nothing>
    /** No connection, or the server is down. */
    data object Offline : CatalogResult<Nothing>
}

/**
 * The car form's lookup. Never throws: every failure turns into a [CatalogResult] the form can
 * explain, and manual entry carries on regardless.
 */
class VehicleCatalog(private val api: TrackBoardVehicleSpecsApi, private val auth: AuthRepository) {

    val isSignedIn: Boolean get() = auth.isSignedIn

    suspend fun carMakes() = lookup { api.carMakes(it) }

    suspend fun carModels(make: String) = lookup { api.carModels(it, make) }

    suspend fun carYears(make: String, model: String) = lookup { api.carYears(it, make, model) }

    suspend fun carVariants(make: String, model: String, year: Int) =
        lookup { api.carVariants(it, make, model, year) }

    suspend fun motorcycles(make: String, model: String, year: Int?) =
        lookup { api.motorcycles(it, make, model, year) }

    private suspend fun <T> lookup(call: suspend (String) -> T): CatalogResult<T> {
        if (!auth.isSignedIn) return CatalogResult.SignedOut
        return try {
            CatalogResult.Found(auth.authorized(call))
        } catch (e: ApiException) {
            Log.w("TrackBoard", "Vehicle lookup refused (${e.status})", e)
            if (e.status == 401) CatalogResult.SignedOut else CatalogResult.Unavailable
        } catch (e: NetworkException) {
            CatalogResult.Offline
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // A response we could not read is the catalogue's problem, not the form's.
            Log.w("TrackBoard", "Vehicle lookup failed", e)
            CatalogResult.Unavailable
        }
    }
}
