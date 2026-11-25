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

/** The fallback name used when a release asset does not supply a plain file name. */
private const val LOCAL_APK_NAME = "release.apk"

/**
 * Turns the name of a remote asset into a plain file name inside the updates
 * folder.
 *
 * Release metadata is remote input: a name with a path in it (`../../x`, `a/b`,
 * an absolute path, `.` or `..`) is never used as a file name. Such a name
 * falls back to a fixed local name, so the download always stays in the folder
 * the app owns.
 */
internal fun safeDownloadName(assetName: String): String {
    val name = assetName.trim()
    if (name.isEmpty() || name == "." || name == ".." ||
        name.contains('/') || name.contains('\\') || File(name).name != name
    ) {
        return LOCAL_APK_NAME
    }
    return name
}

/**
 * The canonical name of a digest algorithm this app checks, or null when the
 * algorithm is not one of the two it supports.
 */
fun supportedDigest(algorithm: String): String? = when (
    algorithm.trim().lowercase().replace("-", "")
) {
    "sha256" -> "SHA-256"
    "sha512" -> "SHA-512"
    else -> null
}

/**
 * Downloads the package of a release.
 *
 * The file is written into the app's own storage, first under a temporary name
 * and only given its real name once it is complete, so that a file that was
 * cut short is never mistaken for a finished one.
 */
class ApkDownloader(private val folder: File) {

    companion object {
        /** The largest package this app is willing to write (512 MiB). */
        const val MAX_DOWNLOAD_BYTES = 512L * 1024L * 1024L
    }

    /**
     * Downloads the asset and returns the finished file.
     *
     * [onProgress] is called with the number of bytes written so far and the
     * total size, when the server says how large the file is.
     */
    suspend fun download(asset: ReleaseAsset, onProgress: (Long, Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            if (!folder.exists()) folder.mkdirs()
            // Never build a path out of a name supplied by the release JSON.
            val localName = safeDownloadName(asset.name)
            val target = File(folder, localName)
            val partial = File(folder, "$localName.part")
            if (!SafeLinks.isAllowedReleaseHost(asset.downloadUrl)) {
                throw DownloadFailure("The download does not come from GitHub.")
            }
            val connection = (URL(asset.downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "CalcSuite-Android")
            }
            try {
                val status = connection.responseCode
                // responseCode follows redirects; refuse a redirected address
                // that is no longer one of GitHub's hosts.
                if (!SafeLinks.isAllowedReleaseHost(connection.url.toString())) {
                    throw DownloadFailure("The download was redirected away from GitHub.")
                }
                if (status !in 200..299) {
                    throw DownloadFailure(
                        "The download could not be started (the server answered with $status).",
                    )
                }
                val total = connection.contentLengthLong
                if (total > MAX_DOWNLOAD_BYTES) {
                    throw DownloadFailure("The download is too large to save on this phone.")
                }
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
                            if (written + read > MAX_DOWNLOAD_BYTES) {
                                throw DownloadFailure("The download is too large to save on this phone.")
                            }
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
                // A digest is checked only when the release published one:
                // GitHub does not publish digests for every asset, and the
                // signature check in ApkInstaller (plus the person's own
                // confirmation in the system installer) is the anchor of trust
                // when it is absent. A digest that *is* present must be one we
                // can actually verify, so an unreadable or unsupported one
                // fails instead of silently skipping the check.
                if (asset.digest != null) {
                    val digest = asset.digestParts
                        ?: throw DownloadFailure("The digest of the release could not be read.")
                    val algorithm = supportedDigest(digest.first)
                        ?: throw DownloadFailure("The digest of the release uses an unsupported algorithm.")
                    val actual = hashOf(partial, algorithm)
                        ?: throw DownloadFailure("The download could not be checked.")
                    if (!actual.equals(digest.second, ignoreCase = true)) {
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
 * The digest of a file, or null when the algorithm is not one this app
 * supports. Only SHA-256 and SHA-512 are accepted; a name supplied in release
 * metadata must never choose an arbitrary provider algorithm.
 */
fun hashOf(file: File, algorithm: String): String? {
    val canonical = supportedDigest(algorithm) ?: return null
    val digest = try {
        MessageDigest.getInstance(canonical)
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
