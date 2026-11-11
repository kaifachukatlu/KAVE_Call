package com.kaif.mastercaller.ui.poster

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Cuts the person out of a photo with ML Kit Selfie Segmentation (on-device, bundled model, no network).
 * The result is the same size as the input, transparent everywhere except the person, so it can be
 * drawn exactly on top of the original photo.
 */
object SubjectCutout {
    private const val TAG = "SubjectCutout"

    /** Small cache so the poster doesn't re-segment on every recomposition / screen rotation. */
    private val cache = LruCache<String, Bitmap>(4)

    suspend fun cutout(key: String, photo: Bitmap): Bitmap? {
        cache.get(key)?.let { return it }
        val result = segment(photo) ?: return null
        cache.put(key, result)
        return result
    }

    private suspend fun segment(photo: Bitmap): Bitmap? {
        val segmenter = Segmentation.getClient(
            SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .build(),
        )
        return try {
            val mask = suspendCancellableCoroutine<SegmentationMask?> { cont ->
                segmenter.process(InputImage.fromBitmap(photo, 0))
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Segmentation failed", e)
                        if (cont.isActive) cont.resume(null)
                    }
            } ?: return null

            val w = mask.width
            val h = mask.height
            if (w != photo.width || h != photo.height) return null
            val confidence = mask.buffer.apply { rewind() }.asFloatBuffer()
            val pixels = IntArray(w * h)
            photo.getPixels(pixels, 0, w, 0, 0, w, h)
            var foreground = 0
            for (i in pixels.indices) {
                val c = confidence.get(i)
                // Soft edge between 0.4 and 0.8 confidence, so hair/shoulders blend instead of looking cut out.
                val t = ((c - 0.4f) / 0.4f).coerceIn(0f, 1f)
                val alpha = (t * t * (3 - 2 * t) * 255).toInt()
                if (alpha > 128) foreground++
                pixels[i] = (alpha shl 24) or (pixels[i] and 0x00FFFFFF)
            }
            // No clear person in the photo (landscape, object, pet...): no depth effect.
            val coverage = foreground.toFloat() / pixels.size
            if (coverage < 0.03f) return null
            Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            Log.w(TAG, "Cutout failed", e)
            null
        } finally {
            segmenter.close()
        }
    }
}
