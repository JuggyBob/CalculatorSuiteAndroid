package com.calcsuite.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide whether a published release is newer than the app that
 * is installed.
 */
class AppVersionTest {

    @Test fun anOlderInstalledVersionIsOlder() {
        val installed = AppVersion.parse("3.0.1")!!
        val latest = AppVersion.parse("3.0.2")!!
        assertTrue("3.0.2 is newer than 3.0.1", latest.isNewerThan(installed))
        assertFalse("3.0.1 is not newer than 3.0.2", installed.isNewerThan(latest))
    }

    @Test fun theSameVersionIsNotNewer() {
        val installed = AppVersion.parse("3.0.1")!!
        val latest = AppVersion.parse("3.0.1")!!
        assertFalse("the same version is not newer", latest.isNewerThan(installed))
    }

    @Test fun aNewerInstalledVersionIsNeverOfferedADowngrade() {
        val installed = AppVersion.parse("3.1.0")!!
        val release = AppVersion.parse("3.0.2")!!
        assertFalse("an older release is not newer", release.isNewerThan(installed))
    }

    @Test fun aLeadingVIsAllowed() {
        val tagged = AppVersion.parse("v3.0.2")
        assertNotNull("a tag with a v is a version", tagged)
        assertEquals("the v is not part of the version", "3.0.2", tagged!!.text)
        assertTrue("v3.0.2 is newer than 3.0.1", tagged.isNewerThan(AppVersion.parse("3.0.1")!!))
    }

    @Test fun multiDigitPartsAreComparedAsNumbers() {
        // As text, "9" is larger than "10". As versions, it is not.
        val nine = AppVersion.parse("0.0.9")!!
        val ten = AppVersion.parse("0.0.10")!!
        assertTrue("0.0.10 is newer than 0.0.9", ten.isNewerThan(nine))
        assertFalse("0.0.9 is not newer than 0.0.10", nine.isNewerThan(ten))
    }

    @Test fun aMissingPartCountsAsZero() {
        val three = AppVersion.parse("3")!!
        val threeZero = AppVersion.parse("3.0")!!
        assertFalse("3 and 3.0 are the same version", threeZero.isNewerThan(three))
        assertFalse("3 and 3.0 are the same version", three.isNewerThan(threeZero))
    }

    @Test fun aMalformedVersionIsNotAVersion() {
        assertNull("an empty text is not a version", AppVersion.parse(""))
        assertNull("letters are not a version", AppVersion.parse("three"))
        assertNull("a missing part is not a version", AppVersion.parse("3..1"))
        assertNull("a part with letters is not a version", AppVersion.parse("3.0.x"))
        assertNull("a version with only a v is not a version", AppVersion.parse("v"))
    }

    @Test fun aPreReleaseIsOlderThanTheFinishedVersion() {
        val beta = AppVersion.parse("3.1.0-beta1")!!
        val finished = AppVersion.parse("3.1.0")!!
        assertTrue("the finished version is newer", finished.isNewerThan(beta))
        assertFalse("the pre-release is not newer", beta.isNewerThan(finished))
    }

    @Test fun preReleasePartsAreComparedAsNumbers() {
        val first = AppVersion.parse("3.1.0-rc.2")!!
        val second = AppVersion.parse("3.1.0-rc.10")!!
        assertTrue("rc.10 is newer than rc.2", second.isNewerThan(first))
    }

    @Test fun preReleasePartsThatAreWordsAreComparedAsText() {
        // The rule of the version scheme: a part that is a number is compared
        // as a number, any other part as text.
        val first = AppVersion.parse("3.1.0-beta2")!!
        val second = AppVersion.parse("3.1.0-beta10")!!
        assertTrue("beta2 comes after beta10 as text", first.isNewerThan(second))
    }

    @Test fun aVersionWithTextKeepsItsText() {
        val version = AppVersion.parse("3.0.1")!!
        assertEquals("the version reads as itself", "3.0.1", version.text)
    }
}
