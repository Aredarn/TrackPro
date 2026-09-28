package com.example.trackpro.managerClasses.utilities

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** The part of [PhotoStore] sync needs, so sync can be tested without a Context. */
interface PhotoFiles {
    suspend fun hash(name: String?): String?
    suspend fun bytes(name: String?): ByteArray?
    /** Stores a finished photo and returns its new name. */
    suspend fun importBytes(bytes: ByteArray, prefix: String): String
    fun delete(name: String?)
}

/**
 * Car and profile photos, kept in the app's private storage.
 *
 * Every photo the app shows is a local file: the garage and the profile render offline, and
 * only sync ever talks to the server. Pictures are re-encoded on the way in - rotated upright,
 * capped at [MAX_EDGE_PX], JPEG - so a 12-megapixel camera frame costs a few hundred kilobytes
 * on disk and on the uplink instead of several megabytes.
 */
class PhotoStore(private val context: Context) : PhotoFiles {

    private val dir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    /** The file for a stored name, or null when there is none or it has gone missing. */
    fun file(name: String?): File? = name?.let { File(dir, it) }?.takeIf { it.isFile }

    /** A fresh target for the system camera to write into. Pass to `TakePicture`. */
    fun newCameraTarget(): Uri {
        val camera = File(context.cacheDir, "camera").apply { mkdirs() }
        val target = File(camera, "capture-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.photos", target)
    }

    /**
     * Normalises the picture at [uri] into a new stored file and returns its name, or null
     * when it could not be read as an image. The caller deletes the previous file, once the
     * new name is safely recorded.
     */
    suspend fun import(uri: Uri, prefix: String): String? = withContext(Dispatchers.IO) {
        // A picked file can vanish or lose its grant between picking and reading; that is an
        // unreadable picture to the driver, not a crash.
        try {
            importOrThrow(uri, prefix)
        } catch (e: java.io.IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    private fun importOrThrow(uri: Uri, prefix: String): String? {
        val resolver = context.contentResolver

        // A bounds-only decode always returns null by design: it fills in outWidth/outHeight and
        // nothing else. So "could the stream be opened" has to be answered separately, not by
        // the decode's return value - reading that as failure rejected every photo there was.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val opened = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds); true } ?: false
        if (!opened || bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Power-of-two subsampling gets close cheaply; the exact scale happens after.
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE_PX) sample *= 2

        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null

        val rotation = resolver.openInputStream(uri)?.use { stream ->
            runCatching {
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }.getOrDefault(0f)
        } ?: 0f

        val scale = minOf(1f, MAX_EDGE_PX.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
        val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (upright !== decoded) decoded.recycle()

        val name = "$prefix-${System.currentTimeMillis()}.jpg"
        FileOutputStream(File(dir, name)).use { upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        upright.recycle()

        // A camera capture is a temporary; nothing else will ever clean it up.
        if (uri.authority == "${context.packageName}.photos") {
            runCatching { resolver.delete(uri, null, null) }
        }
        return name
    }

    /** Stores bytes that are already a finished photo, e.g. one downloaded from the account. */
    override suspend fun importBytes(bytes: ByteArray, prefix: String): String = withContext(Dispatchers.IO) {
        val name = "$prefix-${System.currentTimeMillis()}.jpg"
        File(dir, name).writeBytes(bytes)
        name
    }

    override suspend fun bytes(name: String?): ByteArray? = withContext(Dispatchers.IO) { file(name)?.readBytes() }

    /** Content fingerprint, so sync can tell "same photo" from "same file name". */
    override suspend fun hash(name: String?): String? = withContext(Dispatchers.IO) {
        val file = file(name) ?: return@withContext null
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun delete(name: String?) {
        name?.let { File(dir, it).delete() }
    }

    companion object {
        /** Long edge. Sharp on a phone held at arm's length, small on the wire. */
        const val MAX_EDGE_PX = 1280
        private const val JPEG_QUALITY = 85
    }
}
