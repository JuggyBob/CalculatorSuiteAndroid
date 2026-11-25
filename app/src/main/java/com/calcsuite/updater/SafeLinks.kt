package com.calcsuite.updater

import java.net.URI

/**
 * The hosts GitHub uses for release pages, the API and release assets.
 *
 * Metadata read from the network is not trusted to decide which address the
 * app visits. Only explicit https links to these hosts are accepted.
 */
object SafeLinks {

    private const val GITHUB_HOST = "github.com"
    private const val API_HOST = "api.github.com"
    private const val CONTENT_SUFFIX = ".githubusercontent.com"

    /**
     * The release page address, when it is an https link to github.com.
     *
     * Returns null for every other address (including http, custom schemes and
     * look-alike hosts such as `github.com.example.com`), so callers can simply
     * do nothing when there is no safe page to open.
     */
    fun releasePageUrl(address: String): String? {
        val uri = parsed(address) ?: return null
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        if (!scheme.equals("https", ignoreCase = true)) return null
        if (!host.equals(GITHUB_HOST, ignoreCase = true)) return null
        return uri.toString()
    }

    /**
     * True when an address is https on a host GitHub uses for release data.
     *
     * Used for both the address we connect to and the final address after
     * redirects. A subdomain of `githubusercontent.com` is allowed (for
     * example `objects.githubusercontent.com`), but the bare domain is not a
     * GitHub content host and is refused.
     */
    fun isAllowedReleaseHost(address: String): Boolean {
        val uri = parsed(address) ?: return false
        val scheme = uri.scheme ?: return false
        if (!scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host ?: return false
        return host.equals(API_HOST, ignoreCase = true) ||
            host.equals(GITHUB_HOST, ignoreCase = true) ||
            (host.length > CONTENT_SUFFIX.length &&
                host.endsWith(CONTENT_SUFFIX, ignoreCase = true))
    }

    private fun parsed(address: String): URI? = try {
        URI.create(address.trim()).takeIf { it.host != null }
    } catch (e: Exception) {
        null
    }
}
