package com.example.trackpro.online

import com.example.trackpro.dataClasses.RemoteLink
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_VEHICLE
import com.example.trackpro.dataClasses.RemoteLink.Companion.KIND_VEHICLE_PHOTO
import com.example.trackpro.dataClasses.VehicleInformationData
import com.example.trackpro.managerClasses.utilities.PhotoFiles
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The account's garage: vehicles with photos, and the storage those photos live in. */
class FakeAccountApi : TrackBoardAccountApi {
    val calls = mutableListOf<String>()
    val vehicles = linkedMapOf<String, VehicleResponse>()
    val storage = mutableMapOf<String, ByteArray>()
    var photosAvailable = true
    /** Ids the account refuses to delete because uploaded sessions use them. */
    val inUse = mutableSetOf<String>()

    override suspend fun getProfile(accessToken: String) = error("unused")
    override suspend fun updateProfile(accessToken: String, body: UpdateProfileRequest) = error("unused")
    override suspend fun getStats(accessToken: String) = error("unused")
    override suspend fun exportAccount(accessToken: String) = error("unused")
    override suspend fun deleteAccount(accessToken: String, password: String) = error("unused")
    override suspend fun setAvatar(accessToken: String, path: String?) = error("unused")

    override suspend fun listVehicles(accessToken: String, page: Int, pageSize: Int): VehiclePage {
        calls += "LIST $page"
        val items = vehicles.values.drop((page - 1) * pageSize).take(pageSize)
        return VehiclePage(items, page, pageSize, vehicles.size.toLong())
    }

    override suspend fun deleteVehicle(accessToken: String, id: String) {
        calls += "DELETE vehicle $id"
        if (id in inUse) throw ApiException(409, "In use", "VehicleInUse")
        vehicles.remove(id)
    }

    override suspend fun createUpload(accessToken: String, body: UploadRequest): UploadTarget {
        if (!photosAvailable) throw ApiException(503, "Photo storage is not configured on this server.")
        val path = "users/me/vehicles/${body.vehicleId}/${calls.size}.jpg"
        calls += "UPLOAD-URL ${body.vehicleId}"
        return UploadTarget("https://store/upload/$path", path, "https://store/public/$path")
    }

    override suspend fun uploadBytes(url: String, bytes: ByteArray, contentType: String) {
        calls += "PUT bytes"
        storage[url.removePrefix("https://store/upload/")] = bytes
    }

    override suspend fun setVehiclePhoto(accessToken: String, vehicleId: String, path: String?): VehicleResponse {
        calls += "PHOTO $vehicleId ${path != null}"
        val url = path?.let { "https://store/public/$it" }
        val updated = vehicles.getValue(vehicleId).copy(photoUrl = url)
        vehicles[vehicleId] = updated
        return updated
    }

    override suspend fun download(url: String): ByteArray? {
        calls += "GET $url"
        return storage[url.removePrefix("https://store/public/")]
    }

    /** What the core API's vehicle PUT would store, kept here so both sides agree. */
    fun accept(id: String, body: VehicleWrite) {
        val previous = vehicles[id]
        vehicles[id] = body.toResponse(id, previous?.photoUrl)
    }
}

private fun VehicleWrite.toResponse(id: String, photoUrl: String?) = VehicleResponse(
    id = id, manufacturer = manufacturer, model = model, year = year, engineType = engineType,
    horsepower = horsepower, torque = torque, weight = weight, topSpeed = topSpeed,
    acceleration = acceleration, drivetrain = drivetrain, fuelType = fuelType, tireType = tireType,
    fuelCapacity = fuelCapacity, transmission = transmission, suspensionType = suspensionType,
    photoUrl = photoUrl,
)

/** Photo files in memory. The "hash" is the content, which is all sync compares. */
class FakePhotos : PhotoFiles {
    val files = mutableMapOf<String, ByteArray>()
    private var n = 0

    override suspend fun hash(name: String?) = name?.let(files::get)?.decodeToString()
    override suspend fun bytes(name: String?) = name?.let(files::get)
    override suspend fun importBytes(bytes: ByteArray, prefix: String): String =
        "$prefix-${++n}.jpg".also { files[it] = bytes }
    override fun delete(name: String?) { name?.let(files::remove) }
}

class GarageSyncTest {

    private val core = FakeApi()
    private val account = FakeAccountApi()
    private val dao = FakeSyncDao()
    private val photos = FakePhotos()
    private var nextId = 0

    init {
        core.onPutVehicle = {}
    }

    private val sync: GarageSync
        get() = GarageSync(
            api = account,
            core = object : TrackBoardApi by core {
                override suspend fun putVehicle(accessToken: String, id: String, body: VehicleWrite): Boolean {
                    core.putVehicle(accessToken, id, body)
                    account.accept(id, body)
                    return true
                }
            },
            auth = AuthRepository(core, signedInStore()),
            dao = dao,
            photos = photos,
            newId = { "car-${++nextId}" },
            clock = { 1_000L },
        )

    private fun run(): GarageTally = GarageTally().also { runBlocking { sync.run(it) } }

    private fun car(id: Long, model: String = "911 GT3", year: Int = 2023, photo: String? = null) =
        VehicleInformationData(
            vehicleId = id, manufacturer = "Porsche", model = model, year = year, engineType = "F6",
            horsepower = 510, torque = null, weight = 1418.0, topSpeed = null, acceleration = null,
            drivetrain = "RWD", fuelType = "Petrol", tireType = "Slick", fuelCapacity = null,
            transmission = "PDK", suspensionType = null, photoFile = photo,
        ).also { dao.vehicles += it }

    private fun remoteCar(id: String, model: String = "911 GT3", year: Int = 2023, photoUrl: String? = null) {
        account.vehicles[id] = VehicleResponse(
            id = id, manufacturer = "Porsche", model = model, year = year, engineType = "F6", horsepower = 510,
            weight = 1418.0, drivetrain = "RWD", fuelType = "Petrol", tireType = "Slick", transmission = "PDK",
            photoUrl = photoUrl,
        )
    }

    @Test
    fun `a car made on this phone is backed up`() {
        car(1)

        val tally = run()

        assertEquals(listOf("PUT vehicle car-1"), core.puts("vehicle"))
        assertTrue("car-1" in account.vehicles)
        assertEquals(1, tally.uploaded)
        assertNull(dao.links[KIND_VEHICLE to 1L]!!.lastError)
    }

    @Test
    fun `a second run with nothing changed sends nothing`() {
        car(1)
        run()
        core.calls.clear()

        run()

        assertTrue(core.puts("vehicle").isEmpty())
    }

    @Test
    fun `a car on the account but not on this phone is restored, photo included`() {
        account.storage["p/1.jpg"] = "photo-bytes".toByteArray()
        remoteCar("remote-1", photoUrl = "https://store/public/p/1.jpg")

        val tally = run()

        val restored = dao.vehicles.single()
        assertEquals("911 GT3", restored.model)
        assertEquals("photo-bytes", photos.files[restored.photoFile]?.decodeToString())
        assertEquals("remote-1", dao.links[KIND_VEHICLE to restored.vehicleId]!!.remoteId)
        assertEquals(1, tally.downloaded)
        // Restoring must not bounce straight back up.
        assertTrue(core.puts("vehicle").isEmpty())
    }

    @Test
    fun `the same car typed in on a new phone is paired, not duplicated`() {
        remoteCar("remote-1")
        car(1)

        run()

        assertEquals(1, account.vehicles.size)
        assertEquals(1, dao.vehicles.size)
        assertEquals("remote-1", dao.links[KIND_VEHICLE to 1L]!!.remoteId)
    }

    @Test
    fun `an edit made on another phone is pulled down when this phone did not change it`() {
        car(1)
        run()
        account.vehicles["car-1"] = account.vehicles.getValue("car-1").copy(horsepower = 600)

        val tally = run()

        assertEquals(600, dao.vehicles.single().horsepower)
        assertEquals(1, tally.downloaded)
    }

    @Test
    fun `when both sides changed, this phone wins`() {
        car(1)
        run()
        account.vehicles["car-1"] = account.vehicles.getValue("car-1").copy(horsepower = 600)
        dao.vehicles[0] = dao.vehicles[0].copy(horsepower = 520)

        run()

        assertEquals(520, account.vehicles.getValue("car-1").horsepower)
        assertEquals(520, dao.vehicles.single().horsepower)
    }

    @Test
    fun `deleting a car here deletes it from the account`() {
        car(1)
        run()
        dao.vehicles.clear()

        run()

        assertFalse("car-1" in account.vehicles)
        assertNull(dao.links[KIND_VEHICLE to 1L])
    }

    @Test
    fun `a car the account must keep for leaderboard sessions is not restored after deleting it here`() {
        car(1)
        run()
        account.inUse += "car-1"
        dao.vehicles.clear()

        run()
        run()

        assertTrue("car-1" in account.vehicles)
        assertTrue("the car came back", dao.vehicles.isEmpty())
        assertEquals(GarageSync.DELETED_HERE, dao.links[KIND_VEHICLE to 1L]!!.uploadedHash)
    }

    @Test
    fun `a car deleted from the account elsewhere stays here as local-only and is not re-uploaded`() {
        car(1)
        run()
        account.vehicles.remove("car-1")
        core.calls.clear()

        run()
        run()

        assertEquals(1, dao.vehicles.size)
        assertEquals(GarageSync.REMOTE_GONE, dao.links[KIND_VEHICLE to 1L]!!.uploadedHash)
        assertTrue(core.puts("vehicle").isEmpty())
        assertEquals(
            VehicleSyncStatus.LocalOnly,
            vehicleSyncStatus(dao.vehicles.single(), dao.links[KIND_VEHICLE to 1L], signedIn = true)
        )
    }

    @Test
    fun `a photo taken here is uploaded once and attached`() {
        photos.files["mine.jpg"] = "front-three-quarter".toByteArray()
        car(1, photo = "mine.jpg")

        run()
        val callsAfterFirst = account.calls.count { it == "PUT bytes" }
        run()

        assertEquals(1, callsAfterFirst)
        assertEquals(1, account.calls.count { it == "PUT bytes" })
        assertNotNull(account.vehicles.getValue("car-1").photoUrl)
        assertEquals("front-three-quarter", dao.links[KIND_VEHICLE_PHOTO to 1L]!!.uploadedHash)
    }

    @Test
    fun `a photo removed here is removed from the account`() {
        photos.files["mine.jpg"] = "x".toByteArray()
        car(1, photo = "mine.jpg")
        run()
        dao.vehicles[0] = dao.vehicles[0].copy(photoFile = null)

        run()

        assertNull(account.vehicles.getValue("car-1").photoUrl)
        assertNull(dao.links[KIND_VEHICLE_PHOTO to 1L])
    }

    @Test
    fun `a photo changed on another phone replaces the one here`() {
        photos.files["mine.jpg"] = "old".toByteArray()
        car(1, photo = "mine.jpg")
        run()
        account.storage["other.jpg"] = "new".toByteArray()
        account.vehicles["car-1"] = account.vehicles.getValue("car-1").copy(photoUrl = "https://store/public/other.jpg")

        run()

        assertEquals("new", photos.files[dao.vehicles.single().photoFile]?.decodeToString())
        assertFalse("the old file was left behind", "mine.jpg" in photos.files)
    }

    @Test
    fun `a server without photo storage keeps the car backed up and says so once`() {
        account.photosAvailable = false
        photos.files["a.jpg"] = "a".toByteArray()
        photos.files["b.jpg"] = "b".toByteArray()
        car(1, photo = "a.jpg")
        car(2, model = "Cayman", photo = "b.jpg")

        val tally = run()

        assertEquals(2, account.vehicles.size)
        assertEquals(1, tally.failed)
        assertTrue(tally.problem!!.contains("photos"))
    }

    @Test
    fun `a car the account rejects is reported, and the rest still sync`() {
        car(1)
        dao.vehicles += dao.vehicles[0].copy(vehicleId = 2, manufacturer = " ", model = "Nameless")

        val tally = run()

        assertEquals(1, account.vehicles.size)
        assertEquals(1, tally.failed)
    }

    @Test
    fun `status follows the link`() {
        val v = car(1)
        assertEquals(VehicleSyncStatus.OnThisPhone, vehicleSyncStatus(v, null, signedIn = false))
        assertEquals(VehicleSyncStatus.Pending, vehicleSyncStatus(v, null, signedIn = true))
        run()
        assertEquals(VehicleSyncStatus.Synced, vehicleSyncStatus(v, dao.links[KIND_VEHICLE to 1L], signedIn = true))
        val edited = v.copy(horsepower = 600)
        assertEquals(VehicleSyncStatus.Pending, vehicleSyncStatus(edited, dao.links[KIND_VEHICLE to 1L], signedIn = true))
        val failed = RemoteLink(KIND_VEHICLE, 1, "car-1", "x", null, lastError = "Nope")
        assertEquals(VehicleSyncStatus.Failed, vehicleSyncStatus(v, failed, signedIn = true))
    }
}
