package com.calcsuite.updater

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the updater decides when it looks for a newer release.
 */
class UpdateServiceTest {

    /** A release source that answers with a fixed release, or fails. */
    private class FixedSource(
        private val release: ReleaseInfo?,
        private val failure: ReleaseUnavailableException? = null,
    ) : ReleaseSource {
        override suspend fun latestRelease(): ReleaseInfo {
            failure?.let { throw it }
            return release ?: throw ReleaseUnavailableException("There is no published release yet.")
        }
    }

    private fun release(
        tag: String = "v3.0.2",
        withPackage: Boolean = true,
    ): ReleaseInfo = ReleaseInfo(
        name = "Calculator Suite ${tag.removePrefix("v")}",
        tagName = tag,
        version = AppVersion.parse(tag),
        body = "notes",
        publishedAt = "2026-10-20T09:15:00Z",
        htmlUrl = "https://github.com/JuggyBob/CalculatorSuiteAndroid/releases/tag/$tag",
        assets = if (withPackage) {
            listOf(
                ReleaseAsset(
                    name = "CalculatorSuite-${tag.removePrefix("v")}.apk",
                    downloadUrl = "https://example.invalid/app.apk",
                    sizeBytes = 100,
                    contentType = "application/vnd.android.package-archive",
                ),
            )
        } else {
            emptyList()
        },
    )

    @Test fun aNewerReleaseIsOffered() = runTest {
        val service = UpdateService(FixedSource(release()))
        val result = service.check(AppVersion.parse("3.0.1")!!)
        assertTrue("an update should be offered", result is UpdateCheck.Available)
        val available = result as UpdateCheck.Available
        assertEquals("the release is the newer one", "v3.0.2", available.release.tagName)
        assertEquals("the package is the one of the release", "CalculatorSuite-3.0.2.apk", available.asset.name)
    }

    @Test fun theSameVersionIsNotAnUpdate() = runTest {
        val service = UpdateService(FixedSource(release(tag = "v3.0.1")))
        val result = service.check(AppVersion.parse("3.0.1")!!)
        assertTrue("there is nothing to offer", result is UpdateCheck.UpToDate)
    }

    @Test fun anOlderReleaseIsNeverOffered() = runTest {
        val service = UpdateService(FixedSource(release(tag = "v3.0.1")))
        val result = service.check(AppVersion.parse("3.1.0")!!)
        assertTrue("an older release is not an update", result is UpdateCheck.UpToDate)
    }

    @Test fun aReleaseWithoutAPackageIsNotOfferedAsAnUpdate() = runTest {
        val service = UpdateService(FixedSource(release(withPackage = false)))
        val result = service.check(AppVersion.parse("3.0.1")!!)
        assertTrue("there is nothing to install", result is UpdateCheck.NoInstallableAsset)
        assertNotNull("the release is still known", (result as UpdateCheck.NoInstallableAsset).release)
    }

    @Test fun aReleaseWhoseVersionCannotBeReadIsNotOffered() = runTest {
        val broken = release().copy(version = null, tagName = "nightly")
        val service = UpdateService(FixedSource(broken))
        val result = service.check(AppVersion.parse("3.0.1")!!)
        assertTrue("an unreadable version is not an update", result is UpdateCheck.Unreadable)
        assertTrue(
            "the message says what happened",
            (result as UpdateCheck.Unreadable).message.isNotEmpty(),
        )
    }

    @Test fun aFailureIsReported() = runTest {
        val service = UpdateService(FixedSource(null, ReleaseUnavailableException("There is no connection.")))
        val result = service.check(AppVersion.parse("3.0.1")!!)
        assertTrue("the failure is reported", result is UpdateCheck.Unreadable)
        assertEquals(
            "the message is the one of the failure",
            "There is no connection.",
            (result as UpdateCheck.Unreadable).message,
        )
    }
}
