package com.example.trackpro.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trackpro.extrasForUI.TrackProTheme
import com.example.trackpro.theme.TrackProType
import com.example.trackpro.theme.atSize
import com.example.trackpro.theme.bezel
import com.example.trackpro.theme.field
import com.example.trackpro.theme.markingDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A photo in a square-cornered aperture, from a local file.
 *
 * With no photo the aperture is still drawn — field ground, bezel edge, and either the
 * driver's initials or a car mark — so a garage of unphotographed cars reads as a set of
 * empty frames rather than a layout that jumps when the first photo arrives.
 */
@Composable
fun PhotoFrame(
    file: File?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    /** Shown instead of the car mark when there is no photo, e.g. a driver's initials. */
    initials: String? = null,
    shape: androidx.compose.ui.graphics.Shape = com.example.trackpro.theme.TrackProShapes.control,
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(shape)
            .background(TrackProTheme.colors.bgElevated),
        contentAlignment = Alignment.Center
    ) {
        val targetPx = with(LocalDensity.current) { maxOf(maxWidth, maxHeight).toPx().toInt() }.coerceAtLeast(1)
        val bitmap by produceState<ImageBitmap?>(initialValue = file?.let { PhotoCache.peek(it) }, file?.path, file?.lastModified(), targetPx) {
            value = file?.let { PhotoCache.load(it, targetPx) }
        }

        val shown = bitmap
        if (shown != null) {
            Image(
                bitmap = shown,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (initials != null) {
            Text(
                text = initials,
                style = TrackProType.titleLarge.atSize((maxHeight.value * 0.36f).coerceIn(15f, 40f).sp),
                color = TrackProTheme.colors.accent
            )
        } else {
            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = contentDescription,
                tint = TrackProTheme.colors.markingDim,
                modifier = Modifier.size((minOf(maxWidth, maxHeight) * 0.36f).coerceIn(16.dp, 48.dp))
            )
        }
    }
}

/** Up to two letters from a display name, for a photo frame with no photo. */
fun initialsOf(name: String?): String =
    name.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/**
 * Decoded photos, kept across screens so switching tabs does not re-decode the garage.
 * Keyed by path, modification time and size, so a replaced photo is never served stale.
 */
private object PhotoCache {
    private val cache = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 16).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    private fun key(file: File, px: Int) = "${file.path}|${file.lastModified()}|$px"

    fun peek(file: File): ImageBitmap? = synchronized(cache) {
        cache.snapshot().entries.firstOrNull { it.key.startsWith("${file.path}|${file.lastModified()}|") }?.value
    }

    suspend fun load(file: File, targetPx: Int): ImageBitmap? {
        val key = key(file, targetPx)
        synchronized(cache) { cache.get(key) }?.let { return it }
        return withContext(Dispatchers.IO) {
            if (!file.isFile) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?.asImageBitmap()
                ?.also { synchronized(cache) { cache.put(key, it) } }
        }
    }
}
