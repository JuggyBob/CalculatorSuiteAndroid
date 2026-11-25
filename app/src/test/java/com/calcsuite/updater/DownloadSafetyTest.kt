package com.calcsuite.updater

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The local name of a download and the algorithms used to check it come from
 * remote release metadata, so neither may be trusted as given.
 */
class DownloadSafetyTest {

    private val folder = File(System.getProperty("java.io.tmpdir"), "calcsuite-updater-safety-test")

    private fun staysInside(name: String): Boolean {
        val file = File(folder, safeDownloadName(name)).canonicalFile
        return file.parentFile == folder.canonicalFile
    }

    @Test fun aPlainAssetNameIsKept() {
        assertEquals("CalculatorSuite-3.0.2.apk", safeDownloadName("CalculatorSuite-3.0.2.apk"))
        assertTrue(staysInside("CalculatorSuite-3.0.2.apk"))
    }

    @Test fun aParentPathIsReplacedWithTheFixedName() {
        assertEquals("release.apk", safeDownloadName("../../x"))
        assertTrue("the file must stay in the updates folder", staysInside("../../x"))
    }

    @Test fun aNestedPathIsReplacedWithTheFixedName() {
        assertEquals("release.apk", safeDownloadName("a/b"))
        assertEquals("release.apk", safeDownloadName("sub/dir/app.apk"))
        assertTrue("the file must stay in the updates folder", staysInside("a/b"))
    }

    @Test fun windowsSeparatorsAndAbsolutePathsAreRejected() {
        assertEquals("release.apk", safeDownloadName("a\\b"))
        assertEquals("release.apk", safeDownloadName("C:\\Windows\\x.apk"))
        assertEquals("release.apk", safeDownloadName("/etc/passwd"))
        assertTrue(staysInside("C:\\Windows\\x.apk"))
    }

    @Test fun dotNamesAndBlankNamesAreRejected() {
        assertEquals("release.apk", safeDownloadName(""))
        assertEquals("release.apk", safeDownloadName("   "))
        assertEquals("release.apk", safeDownloadName("."))
        assertEquals("release.apk", safeDownloadName(".."))
        assertTrue(staysInside(".."))
    }

    @Test fun onlySha2AlgorithmsAreSupported() {
        assertEquals("SHA-256", supportedDigest("sha256"))
        assertEquals("SHA-256", supportedDigest("SHA-256"))
        assertEquals("SHA-512", supportedDigest("sha512"))
        assertEquals("SHA-512", supportedDigest("SHA-512"))
        assertNull("md5 is not allowed", supportedDigest("md5"))
        assertNull("sha1 is not allowed", supportedDigest("SHA1"))
        assertNull("an arbitrary provider name is not allowed", supportedDigest("SHA3-256"))
        assertNull("a blank name is not allowed", supportedDigest(""))
    }

    @Test fun hashingRefusesAnUnsupportedAlgorithm() {
        val file = File.createTempFile("calcsuite-digest", ".apk")
        try {
            file.writeBytes("a package".toByteArray())
            val expected = MessageDigest.getInstance("SHA-256")
                .digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
            assertEquals(expected, hashOf(file, "sha256"))
            assertEquals(expected, hashOf(file, "SHA-256"))
            assertNull("md5 must not be computed", hashOf(file, "md5"))
        } finally {
            file.delete()
        }
    }

    @Test fun theSizeCapsAreSet() {
        assertEquals(2 * 1024 * 1024, GitHubReleaseClient.MAX_JSON_BYTES)
        assertEquals(512L * 1024L * 1024L, ApkDownloader.MAX_DOWNLOAD_BYTES)
        assertFalse(GitHubReleaseClient.MAX_JSON_BYTES <= 0)
    }
}
