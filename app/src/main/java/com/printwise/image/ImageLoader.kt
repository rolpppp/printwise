package com.printwise.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.printwise.model.Box
import kotlin.math.max
import kotlin.math.roundToInt

/** Longest edge kept in memory. 4096 px across an 11.7 in page is still ~350 dpi. */
private const val MAX_WORKING_EDGE_PX = 4096

object ImageLoader {
    /** Decodes [uri] upright (EXIF applied) and capped at [MAX_WORKING_EDGE_PX]; all crops refer to this bitmap. */
    fun load(resolver: ContentResolver, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // With inJustDecodeBounds, decodeStream always returns null; only the outWidth/outHeight matter.
        (resolver.openInputStream(uri) ?: return null).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // Halve only while the result stays at or above the cap; scaleDown then trims precisely. Halving until it
        // fits under the cap would throw away up to half the detail (a 4500 px photo would end up 2250 px).
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_WORKING_EDGE_PX) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        bitmap = scaleDown(bitmap, MAX_WORKING_EDGE_PX)
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    }

    fun crop(source: Bitmap, box: Box): Bitmap? {
        val left = box.left.toInt().coerceIn(0, source.width)
        val top = box.top.toInt().coerceIn(0, source.height)
        val right = box.right.roundToInt().coerceIn(0, source.width)
        val bottom = box.bottom.roundToInt().coerceIn(0, source.height)
        if (right <= left || bottom <= top) return null
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }
}
