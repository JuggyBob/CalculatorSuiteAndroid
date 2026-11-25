package com.calcsuite.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Addresses taken from release metadata must never be handed to an implicit
 * ACTION_VIEW or used as a download endpoint unless they are really GitHub's.
 */
class SafeLinksTest {

    @Test fun anHttpsGithubReleasePageIsAllowed() {
        val address = "https://github.com/JuggyBob/CalculatorSuiteAndroid/releases/tag/v3.0.2"
        assertEquals(address, SafeLinks.releasePageUrl(address))
    }

    @Test fun theHostAndSchemeAreComparedWithoutCase() {
        val address = "HTTPS://GITHUB.COM/JuggyBob/CalculatorSuiteAndroid/releases"
        assertEquals(address, SafeLinks.releasePageUrl(address))
    }

    @Test fun surroundingWhitespaceIsIgnored() {
        assertEquals(
            "https://github.com/JuggyBob/CalculatorSuiteAndroid/releases",
            SafeLinks.releasePageUrl("  https://github.com/JuggyBob/CalculatorSuiteAndroid/releases  "),
        )
    }

    @Test fun aNonHttpsAddressIsRefused() {
        assertNull("http is not allowed", SafeLinks.releasePageUrl("http://github.com/JuggyBob/releases"))
        assertNull(
            "a look-alike host is not allowed",
            SafeLinks.releasePageUrl("https://github.com.evil.example/releases"),
        )
    }

    @Test fun nonWebSchemesAreRefused() {
        assertNull("javascript", SafeLinks.releasePageUrl("javascript:alert(1)"))
        assertNull("intent", SafeLinks.releasePageUrl("intent://details#Intent;scheme=evil;end"))
        assertNull("a custom scheme", SafeLinks.releasePageUrl("calcsuite://github.com/releases"))
        assertNull("a data url", SafeLinks.releasePageUrl("data:text/html,<script>alert(1)</script>"))
    }

    @Test fun aBlankAddressIsRefused() {
        assertNull("empty", SafeLinks.releasePageUrl(""))
        assertNull("spaces", SafeLinks.releasePageUrl("   "))
    }

    @Test fun userinfoCannotMakeAnEvilHostLookLikeGithub() {
        assertNull(
            "github.com as user info is not a github host",
            SafeLinks.releasePageUrl("https://github.com@evil.example/releases"),
        )
        assertNull(
            "a backslash trick is not a valid uri",
            SafeLinks.releasePageUrl("https://github.com\\@evil.example/releases"),
        )
    }

    @Test fun onlyGithubHostsAreAllowedForReleaseData() {
        assertTrue(SafeLinks.isAllowedReleaseHost("https://api.github.com/repos/o/r/releases/latest"))
        assertTrue(SafeLinks.isAllowedReleaseHost("https://github.com/o/r/releases/download/v1/app.apk"))
        assertTrue(SafeLinks.isAllowedReleaseHost("https://objects.githubusercontent.com/an/asset"))
        assertTrue(SafeLinks.isAllowedReleaseHost("https://raw.githubusercontent.com/o/r/main/file"))
    }

    @Test fun otherHostsAndSchemesAreNotAllowedForReleaseData() {
        assertFalse(SafeLinks.isAllowedReleaseHost("https://githubusercontent.com/x"))
        assertFalse(SafeLinks.isAllowedReleaseHost("https://github.com.evil.example/x"))
        assertFalse(SafeLinks.isAllowedReleaseHost("https://evil.example/github.com/x"))
        assertFalse(SafeLinks.isAllowedReleaseHost("http://github.com/x"))
        assertFalse(SafeLinks.isAllowedReleaseHost("javascript:alert(1)"))
        assertFalse(SafeLinks.isAllowedReleaseHost(""))
    }
}
