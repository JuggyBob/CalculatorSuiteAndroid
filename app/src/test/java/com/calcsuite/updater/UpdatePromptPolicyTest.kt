package com.calcsuite.updater

import com.calcsuite.ui.update.UpdateUiState
import com.calcsuite.updater.UpdatePromptPolicy.UpdatePromptState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests of the pure rules behind the automatic check and its prompt. */
class UpdatePromptPolicyTest {

    private val none = emptySet<String>()

    @Test
    fun newerReleasePrompts() {
        assertTrue(
            UpdatePromptPolicy.shouldPrompt(
                installedVersion = "3.0.2",
                latestTag = "v3.0.3",
                suppressedTags = none,
                state = UpdatePromptState.Available,
            ),
        )
        // A release with no installable package is still worth announcing.
        assertTrue(
            UpdatePromptPolicy.shouldPrompt(
                installedVersion = "3.0.2",
                latestTag = "3.1.0",
                suppressedTags = none,
                state = UpdatePromptState.NoPackage,
            ),
        )
        // Even a stale UpToDate state must not hide a newer cached release.
        assertTrue(
            UpdatePromptPolicy.shouldPrompt(
                installedVersion = "3.0.2",
                latestTag = "3.0.3",
                suppressedTags = none,
                state = UpdatePromptState.UpToDate,
            ),
        )
    }

    @Test
    fun sameOrOlderReleaseDoesNotPrompt() {
        assertFalse(
            UpdatePromptPolicy.shouldPrompt(
                installedVersion = "3.0.3",
                latestTag = "v3.0.3",
                suppressedTags = none,
                state = UpdatePromptState.Available,
            ),
        )
        assertFalse(
            UpdatePromptPolicy.shouldPrompt(
                installedVersion = "3.0.3",
                latestTag = "3.0.2",
                suppressedTags = none,
                state = UpdatePromptState.Available,
            ),
        )
    }

    @Test
    fun suppressedTagStaysSilent() {
        assertFalse(
            UpdatePromptPolicy.shouldPrompt(
                installedVersion = "3.0.2",
                latestTag = "v3.0.3",
                suppressedTags = setOf("v3.0.3"),
                state = UpdatePromptState.Available,
            ),
        )
    }

    @Test
    fun newerTagAfterSuppressionPromptsAgain() {
        val suppressed = UpdatePromptPolicy.suppressionAfterNeverShow("v3.0.3", none)
        assertEquals(setOf("v3.0.3"), suppressed)
        assertFalse(
            UpdatePromptPolicy.shouldPrompt("3.0.2", "v3.0.3", suppressed, UpdatePromptState.Available),
        )
        assertTrue(
            UpdatePromptPolicy.shouldPrompt("3.0.3", "v3.0.4", suppressed, UpdatePromptState.Available),
        )
    }

    @Test
    fun shouldAutoCheckIsDueWhenNeverChecked() {
        assertTrue(UpdatePromptPolicy.shouldAutoCheck(null, 10_000L))
        assertTrue(UpdatePromptPolicy.shouldAutoCheck(0L, 10_000L))
    }

    @Test
    fun shouldAutoCheckRespectsThreeHourThrottle() {
        val last = 10_000L
        val interval = UpdatePromptPolicy.AUTO_CHECK_INTERVAL_MS
        assertEquals(3L * 60L * 60L * 1000L, interval)
        assertFalse(UpdatePromptPolicy.shouldAutoCheck(last, last + interval - 1))
        assertFalse(UpdatePromptPolicy.shouldAutoCheck(last, last + interval / 2))
    }

    @Test
    fun shouldAutoCheckIsDueAtExactBoundary() {
        val last = 10_000L
        val interval = UpdatePromptPolicy.AUTO_CHECK_INTERVAL_MS
        assertTrue(UpdatePromptPolicy.shouldAutoCheck(last, last + interval))
        assertTrue(UpdatePromptPolicy.shouldAutoCheck(last, last + interval + 1))
    }

    @Test
    fun shouldAutoCheckIsDueWhenClockMovesBackwards() {
        assertTrue(UpdatePromptPolicy.shouldAutoCheck(10_000L, 5_000L))
    }

    @Test
    fun emptyAndNullInputsAreSafe() {
        assertFalse(UpdatePromptPolicy.shouldPrompt(null, null, none, UpdatePromptState.Available))
        assertFalse(UpdatePromptPolicy.shouldPrompt("", "", none, UpdatePromptState.Available))
        assertFalse(UpdatePromptPolicy.shouldPrompt("3.0.2", null, none, UpdatePromptState.Available))
        assertFalse(UpdatePromptPolicy.shouldPrompt("not a version", "v3.0.3", none, UpdatePromptState.Available))
        assertFalse(UpdatePromptPolicy.shouldPrompt("3.0.2", "not a version", none, UpdatePromptState.Available))
        assertFalse(UpdatePromptPolicy.shouldPrompt("3.0.2", "v3.0.3", none, UpdatePromptState.Inert))
        assertEquals(none, UpdatePromptPolicy.suppressionAfterNeverShow(null, none))
        assertEquals(none, UpdatePromptPolicy.suppressionAfterNeverShow("  ", none))
    }

    @Test
    fun suppressionAfterNeverShowOnlyAddsGivenTag() {
        val existing = setOf("v3.0.1", "v3.0.2")
        assertEquals(
            existing + "v3.0.3",
            UpdatePromptPolicy.suppressionAfterNeverShow(" v3.0.3 ", existing),
        )
        assertEquals(
            existing,
            UpdatePromptPolicy.suppressionAfterNeverShow("v3.0.1", existing),
        )
    }

    private fun testRelease(withApk: Boolean = true): ReleaseInfo {
        val assets = if (withApk) {
            listOf(
                ReleaseAsset(
                    name = "CalcSuite-3.0.4.apk",
                    downloadUrl = "https://example.invalid/CalcSuite-3.0.4.apk",
                    sizeBytes = 1024L,
                    contentType = "application/vnd.android.package-archive",
                ),
            )
        } else {
            emptyList()
        }
        return ReleaseInfo(
            name = "Calc Suite 3.0.4",
            tagName = "v3.0.4",
            version = AppVersion.parse("v3.0.4"),
            body = "New things",
            publishedAt = null,
            htmlUrl = "https://example.invalid/release",
            assets = assets,
        )
    }

    private fun cached(withApk: Boolean = true) =
        UpdatePromptPolicy.CachedCandidate(testRelease(withApk))

    @Test
    fun failedStatePassesThroughEvenWithCachedCandidate() {
        val failed = UpdateUiState.Failed("The download failed. Try again.")
        assertSame(failed, UpdatePromptPolicy.dialogStateFor(failed, cached()))
        val shown = UpdatePromptPolicy.dialogStateFor(failed, cached())
        assertTrue(shown is UpdateUiState.Failed)
        assertEquals("The download failed. Try again.", (shown as UpdateUiState.Failed).message)
    }

    @Test
    fun availableDownloadingAndReadyStatesPassThrough() {
        val release = testRelease()
        val asset = release.preferredApk()!!
        val available = UpdateUiState.Available(release, asset)
        val downloading = UpdateUiState.Downloading(release, asset, 10L, 20L)
        val ready = UpdateUiState.Ready(release, java.io.File("update.apk"))
        assertSame(available, UpdatePromptPolicy.dialogStateFor(available, cached()))
        assertSame(downloading, UpdatePromptPolicy.dialogStateFor(downloading, cached()))
        assertSame(ready, UpdatePromptPolicy.dialogStateFor(ready, cached()))
    }

    @Test
    fun noPackageAndUpToDateStatesPassThrough() {
        val release = testRelease()
        val noPackage = UpdateUiState.NoPackage(release)
        val upToDate = UpdateUiState.UpToDate("3.0.4", release)
        assertSame(noPackage, UpdatePromptPolicy.dialogStateFor(noPackage, cached(false)))
        assertSame(upToDate, UpdatePromptPolicy.dialogStateFor(upToDate, cached()))
    }

    @Test
    fun idleAndCheckingWithCachedCandidateSynthesizeAvailable() {
        for (real in listOf(UpdateUiState.Idle, UpdateUiState.Checking)) {
            val shown = UpdatePromptPolicy.dialogStateFor(real, cached())
            assertTrue(shown is UpdateUiState.Available)
            assertEquals("v3.0.4", (shown as UpdateUiState.Available).release.tagName)
        }
    }

    @Test
    fun idleAndCheckingWithCachedCandidateWithoutFileSynthesizeNoPackage() {
        for (real in listOf(UpdateUiState.Idle, UpdateUiState.Checking)) {
            val shown = UpdatePromptPolicy.dialogStateFor(real, cached(false))
            assertTrue(shown is UpdateUiState.NoPackage)
            assertEquals("v3.0.4", (shown as UpdateUiState.NoPackage).release.tagName)
        }
    }

    @Test
    fun noCachedCandidateKeepsRealState() {
        assertSame(UpdateUiState.Idle, UpdatePromptPolicy.dialogStateFor(UpdateUiState.Idle, null))
        assertSame(UpdateUiState.Checking, UpdatePromptPolicy.dialogStateFor(UpdateUiState.Checking, null))
    }
}
