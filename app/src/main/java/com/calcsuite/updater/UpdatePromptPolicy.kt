package com.calcsuite.updater

import com.calcsuite.ui.update.UpdateUiState

/**
 * The platform-free decisions behind the automatic update check.
 *
 * Nothing here touches Android or a view model, so the rules can be checked on
 * their own with ordinary JVM tests. The UI maps its own states to
 * [UpdatePromptState] and asks this object what to do.
 */
object UpdatePromptPolicy {

    /** Three hours: the shortest gap between two automatic checks. */
    const val AUTO_CHECK_INTERVAL_MS: Long = 3L * 60L * 60L * 1000L

    /**
     * The part of the updater state that matters for a prompt.
     *
     * The other states (idle, checking, downloading, ready, failed) are [Inert]:
     * they never cause a prompt to appear on their own.
     */
    enum class UpdatePromptState {
        /** A newer release with a package was found. */
        Available,

        /** The newest published release is the one that is installed. */
        UpToDate,

        /** A newer release was found, but it has no installable package. */
        NoPackage,

        /** No prompt-worthy release was found. */
        Inert,
    }

    /**
     * True when an automatic check is due.
     *
     * A missing timestamp means the app has never checked, so it is due. A
     * clock that moved backwards must not block checks forever, so a negative
     * elapsed time counts as due as well. The boundary is inclusive: exactly
     * one interval after the last check the next one is due.
     */
    fun shouldAutoCheck(
        lastCheckAt: Long?,
        now: Long,
        intervalMs: Long = AUTO_CHECK_INTERVAL_MS,
    ): Boolean {
        if (lastCheckAt == null || lastCheckAt <= 0L) return true
        val elapsed = now - lastCheckAt
        if (elapsed < 0L) return true
        return elapsed >= intervalMs
    }

    /**
     * The set of suppressed tags after "Never show again" for [tag].
     *
     * Only the given tag is added: a newer release keeps its own tag and is
     * therefore free to prompt again. Blank tags change nothing.
     */
    fun suppressionAfterNeverShow(tag: String?, suppressedTags: Set<String>): Set<String> {
        val clean = tag?.trim().orEmpty()
        if (clean.isEmpty()) return suppressedTags
        return suppressedTags + clean
    }

    /**
     * True when the release prompt should be shown.
     *
     * The release must be newer than the installed version, its tag must not
     * be suppressed, and the state must be one that can offer a release. All
     * unreadable inputs fail safe (no prompt) rather than guessing.
     */
    fun shouldPrompt(
        installedVersion: String?,
        latestTag: String?,
        suppressedTags: Set<String>,
        state: UpdatePromptState,
    ): Boolean {
        if (state == UpdatePromptState.Inert) return false
        val tag = latestTag?.trim().orEmpty()
        if (tag.isEmpty() || tag in suppressedTags) return false
        val installed = AppVersion.parse(installedVersion.orEmpty()) ?: return false
        val latest = AppVersion.parse(tag) ?: return false
        return latest > installed
    }

    /** A release read earlier and kept on the device for offline use. */
    data class CachedCandidate(val release: ReleaseInfo)

    /**
     * The state the prompt should render.
     *
     * A live state — including a failure — is passed through unchanged, so an
     * error is never replaced by a make-believe "available" message. Only when
     * the view model has nothing better than Idle/Checking and a cached
     * release exists do we shape that release into an Available/NoPackage
     * state, so the prompt can still offer the download action.
     */
    fun dialogStateFor(realState: UpdateUiState, cached: CachedCandidate?): UpdateUiState {
        if (cached == null) return realState
        if (realState !is UpdateUiState.Idle && realState !is UpdateUiState.Checking) {
            return realState
        }
        val asset = cached.release.preferredApk() ?: cached.release.assets.firstOrNull()
        return if (asset != null) {
            UpdateUiState.Available(cached.release, asset)
        } else {
            UpdateUiState.NoPackage(cached.release)
        }
    }
}
