package com.calcsuite.updater

import org.json.JSONArray
import org.json.JSONObject

/**
 * A file attached to a GitHub release.
 */
data class ReleaseAsset(
    /** The name of the file, e.g. "CalcSuite-3.0.2.apk". */
    val name: String,
    /** The address the file is downloaded from. */
    val downloadUrl: String,
    /** The size of the file in bytes, when the release says so. */
    val sizeBytes: Long,
    /** The content type the server reports, e.g. "application/vnd.android.package-archive". */
    val contentType: String,
    /**
     * The digest GitHub publishes for the file, e.g. "sha256:1a2b...".
     * Null when GitHub does not publish one for the asset.
     */
    val digest: String? = null,
) {
    /** The algorithm and the value of the digest, when there is one. */
    val digestParts: Pair<String, String>?
        get() {
            val value = digest ?: return null
            val colon = value.indexOf(':')
            if (colon <= 0) return null
            val algorithm = value.substring(0, colon).trim().lowercase()
            val hex = value.substring(colon + 1).trim()
            if (algorithm.isEmpty() || hex.isEmpty()) return null
            return algorithm to hex
        }
}

/**
 * A published release of the application.
 */
data class ReleaseInfo(
    /** The title of the release, e.g. "Calc Suite 3.0.2". */
    val name: String,
    /** The tag the release was made from, e.g. "v3.0.2". */
    val tagName: String,
    /** The version the release carries, read from the tag or the title. */
    val version: AppVersion?,
    /** The release notes, as written on GitHub. */
    val body: String,
    /** When the release was published, in the format GitHub uses. */
    val publishedAt: String?,
    /** The page a person can read the release on. */
    val htmlUrl: String,
    /** The files attached to the release. */
    val assets: List<ReleaseAsset> = emptyList(),
) {
    /**
     * The APK to install, or null when there is none that can be chosen without
     * guessing.
     *
     * Source archives, debug builds and symbol files are never installable, so
     * they are ignored. When one release APK is left it is the one. When
     * several are left the choice is ambiguous, and null is returned rather
     * than an arbitrary file.
     */
    fun preferredApk(): ReleaseAsset? {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val installable = apks.filterNot { asset ->
            val name = asset.name.lowercase()
            name.contains("debug") || name.contains("symbol") ||
                name.contains("source") || name.contains("-src")
        }
        if (installable.isEmpty()) return null
        if (installable.size == 1) return installable.first()
        // Several remain: prefer the one that names the version of the
        // release, then the one that says it is a release build.
        val versionText = version?.text
        val named = versionText?.let { text ->
            installable.firstOrNull { it.name.contains(text, ignoreCase = true) }
        }
        if (named != null) return named
        val releaseNamed = installable.firstOrNull { it.name.contains("release", ignoreCase = true) }
        if (releaseNamed != null) return releaseNamed
        return null
    }

    /** True when more than one APK could be installed, so none is chosen. */
    fun hasAmbiguousApk(): Boolean {
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val installable = apks.filterNot { asset ->
            val name = asset.name.lowercase()
            name.contains("debug") || name.contains("symbol") ||
                name.contains("source") || name.contains("-src")
        }
        return installable.size > 1 && preferredApk() == null
    }
}

/**
 * Reads the release information out of the JSON the GitHub API returns.
 *
 * The parsing is separate from the transport, so that the rules can be tested
 * without a network.
 */
object ReleaseParser {

    /** The endpoint of the latest published release of the repository. */
    fun latestReleaseUrl(owner: String, repository: String): String =
        "https://api.github.com/repos/$owner/$repository/releases/latest"

    /**
     * Reads a release from the JSON body of the API.
     *
     * Returns null when the body is not a release at all. A release without a
     * readable version is still returned: its notes can be shown, and the
     * updater then falls back to the release page.
     */
    fun parse(body: String): ReleaseInfo? {
        val root = try {
            JSONObject(body)
        } catch (e: Exception) {
            return null
        }
        if (!root.has("tag_name")) return null
        val tagName = root.optString("tag_name")
        val name = root.optString("name").ifEmpty { tagName }
        val bodyText = root.optString("body").ifEmpty { "" }
        val published = if (root.has("published_at")) root.optString("published_at") else null
        val url = root.optString("html_url")
        val assets = mutableListOf<ReleaseAsset>()
        val array: JSONArray? = root.optJSONArray("assets")
        if (array != null) {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val assetName = item.optString("name")
                val download = item.optString("browser_download_url")
                if (assetName.isEmpty() || download.isEmpty()) continue
                assets.add(
                    ReleaseAsset(
                        name = assetName,
                        downloadUrl = download,
                        sizeBytes = item.optLong("size", 0L),
                        contentType = item.optString("content_type"),
                        digest = if (item.has("digest")) item.optString("digest") else null,
                    ),
                )
            }
        }
        return ReleaseInfo(
            name = name,
            tagName = tagName,
            version = AppVersion.parse(tagName) ?: AppVersion.parse(name),
            body = bodyText,
            publishedAt = published,
            htmlUrl = url,
            assets = assets,
        )
    }

    /**
     * Writes a release as JSON, in the same shape the GitHub API uses.
     *
     * The updater keeps the last release it read in this form, so that the
     * About screen can show the notes of the last release even when there is
     * no connection.
     */
    fun toJson(release: ReleaseInfo): String {
        val root = JSONObject()
        root.put("tag_name", release.tagName)
        root.put("name", release.name)
        root.put("body", release.body)
        if (release.publishedAt != null) root.put("published_at", release.publishedAt)
        root.put("html_url", release.htmlUrl)
        val assets = JSONArray()
        for (asset in release.assets) {
            val item = JSONObject()
            item.put("name", asset.name)
            item.put("browser_download_url", asset.downloadUrl)
            item.put("size", asset.sizeBytes)
            item.put("content_type", asset.contentType)
            if (asset.digest != null) item.put("digest", asset.digest)
            assets.put(item)
        }
        root.put("assets", assets)
        return root.toString()
    }
}
