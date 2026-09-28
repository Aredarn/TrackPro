package com.example.trackpro

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.trackpro.managerClasses.utilities.PhotoStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs the real BitmapFactory, which is the point: the first version of [PhotoStore.import]
 * read the always-null result of a bounds-only decode as "unreadable" and refused every photo.
 * A JVM test with a fake decoder could never have caught that.
 */
@RunWith(AndroidJUnit4::class)
class PhotoStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = PhotoStore(context)
    private val created = mutableListOf<String>()
    private val scratch = File(context.cacheDir, "photo-store-test").apply { mkdirs() }

    @After
    fun cleanUp() {
        created.forEach(store::delete)
        scratch.deleteRecursively()
    }

    private fun jpeg(width: Int, height: Int): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
        val file = File(scratch, "in-${width}x$height.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(file)
    }

    @Test
    fun a_normal_photo_is_imported() = runBlocking {
        val name = store.import(jpeg(800, 600), "vehicle-1")

        assertNotNull("a readable JPEG was refused", name)
        created += name!!
        val stored = store.file(name)!!
        val decoded = BitmapFactory.decodeFile(stored.path)
        assertEquals(800, decoded.width)
        assertEquals(600, decoded.height)
    }

    @Test
    fun a_camera_sized_photo_is_scaled_to_the_long_edge_cap() = runBlocking {
        val name = store.import(jpeg(4000, 3000), "vehicle-2")!!
        created += name

        val decoded = BitmapFactory.decodeFile(store.file(name)!!.path)
        assertEquals(PhotoStore.MAX_EDGE_PX, maxOf(decoded.width, decoded.height))
        assertTrue("aspect ratio drifted", kotlin.math.abs(decoded.width / decoded.height.toFloat() - 4f / 3f) < 0.01f)
    }

    @Test
    fun a_file_that_is_not_a_picture_is_refused() = runBlocking {
        val file = File(scratch, "notes.jpg").apply { writeText("definitely not a jpeg") }

        assertNull(store.import(Uri.fromFile(file), "vehicle-3"))
    }

    @Test
    fun a_missing_file_is_refused_rather_than_crashing() = runBlocking {
        assertNull(store.import(Uri.fromFile(File(scratch, "gone.jpg")), "vehicle-4"))
    }
}
