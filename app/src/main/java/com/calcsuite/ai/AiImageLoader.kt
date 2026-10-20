// The platform's own picture reader is used rather than the androidx one,
// because it reads the orientation from API 24, which is below the app's
// own minimum, and it saves a dependency.
@file:Suppress("ExifInterface")

package com.calcsuite.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.core.graphics.scale
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * Reads a picture that the user chose or took.
 *
 * The picture is turned the way the camera held it, so a photograph taken
 * sideways is sent the right way up, and it is made smaller when it is very
 * large, because the providers resize it anyway and a smaller picture is
 * quicker to send.
 */
object AiImageLoader {

    /** The longest side that is sent, in pixels. */
    const val MAX_SIDE = 1600

    /** The pictures that are too large to send. */
    const val MAX_BYTES = 8 * 1024 * 1024

    /** The quality the picture is saved at. */
    private const val QUALITY = 90

    /** The result of reading a picture. */
    sealed interface Result {
        /** The picture was read and is ready to send. */
        data class Ready(val image: AiImage) : Result

        /** The picture could not be read. */
        data class Failed(val message: String) : Result
    }

    /**
     * Reads the picture at the address.
     *
     * The bytes are read first and the type is taken from them, so a
     * picture with the wrong name is still read as what it is.
     */
    fun load(context: Context, uri: Uri): Result {
        val raw = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: return Result.Failed("That picture could not be opened.")

        val mediaType = AiImage.mediaTypeOf(raw)
            ?: return Result.Failed(
                "That picture is not a JPEG, PNG or WebP file, so it cannot be read.",
            )

        if (raw.size > MAX_BYTES) {
            return Result.Failed("That picture is too large to send.")
        }

        val rotation = readRotation(context, uri)
        val scaled = resize(raw, rotation)

        return when (scaled) {
            null -> Result.Failed("That picture could not be decoded.")
            else -> Result.Ready(scaled)
        }
    }

    /**
     * The way the picture has to be turned.
     *
     * The camera records it, so the picture is read back the right way up.
     */
    private fun readRotation(context: Context, uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            ).let { orientation ->
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        } ?: 0
    } catch (e: Exception) {
        0
    }

    /**
     * Turns the picture and makes it smaller when it is large.
     *
     * The picture is saved as a JPEG, which every provider reads, and the
     * quality is kept high so that small symbols and handwriting survive.
     */
    private fun resize(raw: ByteArray, rotation: Int): AiImage? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // The picture is first made small enough to decode, and then to the
        // size that is sent.
        val sample = sampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, options) ?: return null

        val turned = if (rotation == 0) {
            decoded
        } else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val turned = Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                matrix,
                true,
            )
            if (turned != decoded) decoded.recycle()
            turned
        }

        val longest = maxOf(turned.width, turned.height)
        val final = if (longest > MAX_SIDE) {
            val factor = MAX_SIDE.toDouble() / longest
            val smaller = turned.scale(
                (turned.width * factor).toInt().coerceAtLeast(1),
                (turned.height * factor).toInt().coerceAtLeast(1),
            )
            if (smaller != turned) turned.recycle()
            smaller
        } else {
            turned
        }

        val out = ByteArrayOutputStream()
        final.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        val bytes = out.toByteArray()
        final.recycle()
        if (bytes.isEmpty()) return null
        return AiImage(bytes, AiImage.JPEG)
    }

    /** How much the picture is made smaller before it is decoded. */
    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= MAX_SIDE) {
            longest /= 2
            sample *= 2
        }
        return sample
    }
}
