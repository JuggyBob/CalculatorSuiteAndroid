package com.calcsuite.ui.ai

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.calcsuite.ai.AiImage
import java.io.File

/**
 * Where the camera may write the photograph.
 *
 * The app has no camera of its own: it asks the phone's camera app to take
 * the picture and gives it somewhere to put it. The file is in the app's own
 * storage, and it is made by the app, so nothing is asked of the user and
 * no permission is needed.
 */
object AiCameraUri {

    /** The name of the file the picture is written to. */
    private const val FILE_NAME = "ai-problem.jpg"

    /** The place the photograph is written to. */
    private fun file(context: Context): File =
        File(context.cacheDir, "ai").apply {
            if (!exists()) mkdirs()
        }

    /**
     * A place for the camera to write to.
     *
     * The file is emptied first, because the camera app overwrites it
     * rather than adding to it.
     */
    fun create(context: Context): Uri {
        val target = file(context).let { directory ->
            File(directory, FILE_NAME).apply {
                if (exists()) delete()
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }
}

/**
 * Turns the bytes of a picture into a picture the screen can show.
 */
internal fun ByteArray.toBitmap(): android.graphics.Bitmap? = try {
    BitmapFactory.decodeByteArray(this, 0, size)
} catch (e: Exception) {
    null
}
