package com.calcsuite.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading the release information the GitHub API returns, and choosing the
 * package to install out of the files of a release.
 */
class ReleaseInfoTest {

    private val releaseJson = """
        {
          "tag_name": "v3.0.2",
          "name": "Calculator Suite 3.0.2",
          "body": "## Fixed\n- a thing\n",
          "published_at": "2026-10-20T09:15:00Z",
          "html_url": "https://github.com/JuggyBob/CalculatorSuiteAndroid/releases/tag/v3.0.2",
          "assets": [
            {
              "name": "CalculatorSuite-3.0.2.apk",
              "browser_download_url": "https://github.com/JuggyBob/CalculatorSuiteAndroid/releases/download/v3.0.2/CalculatorSuite-3.0.2.apk",
              "size": 14833761,
              "content_type": "application/vnd.android.package-archive",
              "digest": "sha256:95d3b23c9f61d83f13a6878a6424f36baa8de36caab7689e7ec312a7bce9cfcd"
            },
            {
              "name": "source.zip",
              "browser_download_url": "https://github.com/JuggyBob/CalculatorSuiteAndroid/releases/download/v3.0.2/source.zip",
              "size": 1024,
              "content_type": "application/zip"
            }
          ]
        }
    """.trimIndent()

    @Test fun theReleaseIsRead() {
        val release = ReleaseParser.parse(releaseJson)
        assertNotNull("the release should be read", release)
        assertEquals("the tag is read", "v3.0.2", release!!.tagName)
        assertEquals("the title is read", "Calculator Suite 3.0.2", release.name)
        assertEquals("the version is read from the tag", "3.0.2", release.version?.text)
        assertEquals(
            "the notes are read",
            "## Fixed\n- a thing\n",
            release.body,
        )
        assertEquals(
            "the address of the release is read",
            "https://github.com/JuggyBob/CalculatorSuiteAndroid/releases/tag/v3.0.2",
            release.htmlUrl,
        )
    }

    @Test fun theFilesOfTheReleaseAreRead() {
        val release = ReleaseParser.parse(releaseJson)!!
        assertEquals("there are two files", 2, release.assets.size)
        val apk = release.assets.first()
        assertEquals("the name is read", "CalculatorSuite-3.0.2.apk", apk.name)
        assertEquals("the size is read", 14833761L, apk.sizeBytes)
        assertEquals(
            "the content type is read",
            "application/vnd.android.package-archive",
            apk.contentType,
        )
        val digest = apk.digestParts
        assertNotNull("the digest is read", digest)
        assertEquals("the algorithm of the digest", "sha256", digest!!.first)
    }

    @Test fun theInstallablePackageIsChosen() {
        val release = ReleaseParser.parse(releaseJson)!!
        val apk = release.preferredApk()
        assertNotNull("the package should be chosen", apk)
        assertEquals("the package is the release", "CalculatorSuite-3.0.2.apk", apk!!.name)
    }

    @Test fun aDebugBuildIsNotOffered() {
        val release = ReleaseParser.parse(
            """
            {
              "tag_name": "v3.0.2",
              "name": "Calculator Suite 3.0.2",
              "assets": [
                {"name": "app-debug.apk", "browser_download_url": "https://example.invalid/debug.apk"},
                {"name": "app-release.apk", "browser_download_url": "https://example.invalid/release.apk"}
              ]
            }
            """.trimIndent(),
        )!!
        val apk = release.preferredApk()
        assertNotNull("a package should be chosen", apk)
        assertEquals("the release build is the one", "app-release.apk", apk!!.name)
    }

    @Test fun severalPackagesWithNoClearChoiceAreNotGuessed() {
        val release = ReleaseParser.parse(
            """
            {
              "tag_name": "v3.0.2",
              "name": "Calculator Suite 3.0.2",
              "assets": [
                {"name": "one.apk", "browser_download_url": "https://example.invalid/one.apk"},
                {"name": "two.apk", "browser_download_url": "https://example.invalid/two.apk"}
              ]
            }
            """.trimIndent(),
        )!!
        assertNull("no package is chosen", release.preferredApk())
        assertTrue("the release is known to be ambiguous", release.hasAmbiguousApk())
    }

    @Test fun aReleaseWithoutFilesHasNoPackage() {
        val release = ReleaseParser.parse(
            """{"tag_name": "v3.0.2", "name": "Calculator Suite 3.0.2", "assets": []}""",
        )!!
        assertNull("there is no package", release.preferredApk())
    }

    @Test fun aBodyThatIsNotAReleaseIsRefused() {
        assertNull("a reply that is not a release is refused", ReleaseParser.parse("not json"))
        assertNull("a reply without a tag is refused", ReleaseParser.parse("""{"name": "x"}"""))
    }

    @Test fun theReleaseSurvivesBeingWrittenAndReadAgain() {
        val release = ReleaseParser.parse(releaseJson)!!
        val again = ReleaseParser.parse(ReleaseParser.toJson(release))
        assertNotNull("the written release should be read again", again)
        assertEquals("the tag is the same", release.tagName, again!!.tagName)
        assertEquals("the notes are the same", release.body, again.body)
        assertEquals("there are the same files", release.assets.size, again.assets.size)
        assertEquals(
            "the package is still chosen",
            "CalculatorSuite-3.0.2.apk",
            again.preferredApk()?.name,
        )
    }

    @Test fun theAddressOfTheLatestReleaseIsTheOneOfTheRepository() {
        assertEquals(
            "the address of the latest release",
            "https://api.github.com/repos/JuggyBob/CalculatorSuiteAndroid/releases/latest",
            ReleaseParser.latestReleaseUrl(RELEASE_OWNER, RELEASE_REPOSITORY),
        )
    }
}
