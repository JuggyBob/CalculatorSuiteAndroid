package com.calcsuite.updater

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * What a check for updates found.
 */
sealed interface UpdateCheck {
    /** The installed version is the newest one. */
    data class UpToDate(val installed: AppVersion, val release: ReleaseInfo) : UpdateCheck

    /** A newer version is published, and its package can be installed. */
    data class Available(val release: ReleaseInfo, val asset: ReleaseAsset) : UpdateCheck

    /**
     * A newer version is published, but no package can be chosen for it. The
     * release page is the way out.
     */
    data class NoInstallableAsset(val release: ReleaseInfo) : UpdateCheck

    /** The check could not be finished. The message says why. */
    data class Unreadable(val message: String) : UpdateCheck
}

/**
 * Asks the release source whether a newer version exists.
 */
class UpdateService(private val source: ReleaseSource) {

    /**
     * Compares the installed version with the newest published release.
     *
     * A release whose version cannot be read is never offered as an update:
     * the checker fails safely and the screen points at the release page.
     */
    suspend fun check(installed: AppVersion): UpdateCheck {
        val release = try {
            source.latestRelease()
        } catch (e: ReleaseUnavailableException) {
            return UpdateCheck.Unreadable(e.message ?: "The newest release could not be read.")
        }
        val version = release.version
            ?: return UpdateCheck.Unreadable(
                "The version of the newest release could not be read. Open the release page to see it.",
            )
        if (version <= installed) return UpdateCheck.UpToDate(installed, release)
        val asset = release.preferredApk()
            ?: return UpdateCheck.NoInstallableAsset(release)
        return UpdateCheck.Available(release, asset)
    }
}

/** Thrown when a package could not be downloaded. */
class DownloadFailure(message: String) : Exception(message)

/**
 * Downloads the package of a release.
 *
 * The file is written into the app's own storage, first under a temporary name
 * and only given its real name once it is complete, so that a file that was
 * cut short is never mistaken for a finished one.
 */
class ApkDownloader(private val folder: File) {

    /**
     * Downloads the asset and returns the finished file.
     *
     * [onProgress] is called with the number of bytes written so far and the
     * total size, when the server says how large the file is.
     */
    suspend fun download(asset: ReleaseAsset, onProgress: (Long, Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            if (!folder.exists()) folder.mkdirs()
            val target = File(folder, asset.name)
            val partial = File(folder, asset.name + ".part")
            val connection = (URL(asset.downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "CalcSuite-Android")
            }
            try {
                val status = connection.responseCode
                if (status !in 200..299) {
                    throw DownloadFailure(
                        "The download could not be started (the server answered with $status).",
                    )
                }
                val total = connection.contentLengthLong
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val chunk = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            if (!currentCoroutineContext().isActive) {
                                throw CancellationException("The download was cancelled.")
                            }
                            val read = input.read(chunk)
                            if (read <= 0) break
                            output.write(chunk, 0, read)
                            written += read
                            onProgress(written, if (total > 0) total else 0L)
                        }
                    }
                }
                if (partial.length() == 0L) {
                    throw DownloadFailure("The download produced an empty file.")
                }
                if (total > 0 && partial.length() != total) {
                    throw DownloadFailure("The download was cut short. Try again.")
                }
                // The digest the release publishes, when there is one.
                val digest = asset.digestParts
                if (digest != null) {
                    val algorithm = digest.first
                    val expected = digest.second
                    val actual = hashOf(partial, algorithm)
                        ?: throw DownloadFailure("The download could not be checked.")
                    if (!actual.equals(expected, ignoreCase = true)) {
                        throw DownloadFailure(
                            "The download does not match the digest of the release. It was not saved.",
                        )
                    }
                }
                if (target.exists() && !target.delete()) {
                    throw DownloadFailure("The old download could not be replaced.")
                }
                if (!partial.renameTo(target)) {
                    throw DownloadFailure("The download could not be saved.")
                }
                target
            } catch (e: CancellationException) {
                partial.delete()
                throw e
            } catch (e: DownloadFailure) {
                partial.delete()
                throw e
            } catch (e: UnknownHostException) {
                partial.delete()
                throw DownloadFailure("There is no connection to the internet.")
            } catch (e: SocketTimeoutException) {
                partial.delete()
                throw DownloadFailure("The download timed out.")
            } catch (e: IOException) {
                partial.delete()
                throw DownloadFailure("The download failed. Try again.")
            } finally {
                connection.disconnect()
            }
        }

    /** Removes the files of earlier downloads. */
    fun clear() {
        folder.listFiles()?.forEach { it.delete() }
    }
}

/**
 * The digest of a file, or null when the algorithm is not one that is known.
 */
fun hashOf(file: File, algorithm: String): String? {
    val digest = try {
        MessageDigest.getInstance(algorithm)
    } catch (e: Exception) {
        return null
    }
    file.inputStream().use { input ->
        val chunk = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(chunk)
            if (read <= 0) break
            digest.update(chunk, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
