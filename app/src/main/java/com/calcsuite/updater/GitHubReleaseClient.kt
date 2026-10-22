package com.calcsuite.updater

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The owner of the repository the releases are published in. */
const val RELEASE_OWNER = "JuggyBob"

/** The repository the releases are published in. */
const val RELEASE_REPOSITORY = "CalculatorSuiteAndroid"

/**
 * Where the information about the newest release comes from.
 *
 * The updater and the About screen both use this, so that they always show
 * the same release.
 */
interface ReleaseSource {
    /** The newest published release. */
    suspend fun latestRelease(): ReleaseInfo
}

/**
 * Thrown when the release information cannot be read.
 *
 * The message is meant for the person using the app: it says what went wrong
 * in plain words.
 */
class ReleaseUnavailableException(message: String) : Exception(message)

/**
 * Reads the newest release from the GitHub releases API.
 *
 * The API is public, so no token is needed and none is kept in the app. The
 * request is made on a background thread and never blocks the screen.
 */
class GitHubReleaseClient(
    private val owner: String = RELEASE_OWNER,
    private val repository: String = RELEASE_REPOSITORY,
) : ReleaseSource {

    override suspend fun latestRelease(): ReleaseInfo = withContext(Dispatchers.IO) {
        val address = ReleaseParser.latestReleaseUrl(owner, repository)
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "CalcSuite-Android")
        }
        try {
            val status = connection.responseCode
            val stream: InputStream? =
                if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { readAll(it) } ?: ""
            when {
                status == 404 ->
                    throw ReleaseUnavailableException("There is no published release yet.")
                status == 403 || status == 429 ->
                    throw ReleaseUnavailableException(
                        "GitHub is asking for fewer requests just now. Try again in a little while.",
                    )
                status !in 200..299 ->
                    throw ReleaseUnavailableException(
                        "GitHub could not be reached (it answered with $status).",
                    )
            }
            ReleaseParser.parse(text) ?: throw ReleaseUnavailableException(
                "The information about the newest release could not be read.",
            )
        } catch (e: ReleaseUnavailableException) {
            throw e
        } catch (e: UnknownHostException) {
            throw ReleaseUnavailableException("There is no connection to the internet.")
        } catch (e: SocketTimeoutException) {
            throw ReleaseUnavailableException("GitHub did not answer in time.")
        } catch (e: Exception) {
            throw ReleaseUnavailableException("The newest release could not be read.")
        } finally {
            connection.disconnect()
        }
    }

    private fun readAll(stream: InputStream): String {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (true) {
            val read = stream.read(chunk)
            if (read <= 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toString(Charsets.UTF_8.name())
    }
}
