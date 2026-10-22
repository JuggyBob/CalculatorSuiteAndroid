package com.calcsuite.updater

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

/**
 * What the app knows about itself: the package, the version name and the
 * version code that Android compares when it installs an update.
 */
data class InstalledApp(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
) {
    /** The version name as a version, when it can be read as one. */
    val version: AppVersion? get() = AppVersion.parse(versionName)
}

/**
 * What the check of a downloaded package found.
 */
sealed interface InstallCheck {
    /** The package can be installed over the installed app. */
    data object Valid : InstallCheck

    /** The package is for another application. */
    data class WrongPackage(val found: String?) : InstallCheck

    /** The package is not newer than the installed app. */
    data class NotNewer(val versionCode: Long) : InstallCheck

    /** The package is signed with another certificate. */
    data class WrongSignature(val found: String) : InstallCheck

    /** The package could not be read at all. */
    data class Unreadable(val reason: String) : InstallCheck
}

/**
 * Checks a downloaded package and starts the installer for it.
 *
 * The installer is the one of Android itself: the app never installs anything
 * by itself, and never pretends that it has. Android asks the person using the
 * phone to confirm, and it refuses a package that is signed with another
 * certificate.
 */
class ApkInstaller(private val context: Context) {

    /** The package, version name and version code of the installed app. */
    fun installed(): InstalledApp {
        val manager = context.packageManager
        val info = manager.getPackageInfo(context.packageName, 0)
        return InstalledApp(
            packageName = info.packageName,
            versionName = info.versionName ?: "",
            versionCode = versionCodeOf(info),
        )
    }

    /** True when the app may ask Android to install a package. */
    fun canRequestInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * The intent that opens the system's screen for "install unknown apps",
     * where the permission for this app is given.
     */
    fun unknownSourcesIntent(): Intent {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
        intent.data = "package:${context.packageName}".toUri()
        return intent
    }

    /**
     * The intent that starts the installer for the downloaded package.
     *
     * The file is shared through the app's own file provider, so that no
     * path on the phone is exposed.
     */
    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return intent
    }

    /**
     * Reads the downloaded package and says whether it may be installed.
     *
     * The package must be this application, it must be newer than the
     * installed one, and it must be signed with the same certificate. Android
     * checks the last point again when it installs, but saying so here means
     * the person is told before the installer opens.
     */
    @SuppressLint("InlinedApi")
    fun check(file: File): InstallCheck {
        val manager = context.packageManager
        // The flag that asks for the signature only exists from Android 9; on
        // the older systems the older flag is used instead.
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val info = try {
            manager.getPackageArchiveInfo(file.absolutePath, flags)
        } catch (e: Exception) {
            null
        } ?: return InstallCheck.Unreadable("The downloaded package could not be read.")

        if (info.packageName != context.packageName) {
            return InstallCheck.WrongPackage(info.packageName)
        }
        val code = versionCodeOf(info)
        val installed = installed()
        if (code <= installed.versionCode) {
            return InstallCheck.NotNewer(code)
        }
        val downloaded = signaturesOf(info)
        val mine = signaturesOf(installedInfo())
        if (downloaded.isEmpty() || mine.isEmpty()) {
            return InstallCheck.Unreadable("The signature of the downloaded package could not be read.")
        }
        if (!sameSignature(downloaded, mine)) {
            return InstallCheck.WrongSignature(downloaded.joinToString(", ") { it.toString() })
        }
        return InstallCheck.Valid
    }

    private fun installedInfo(): PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)

    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun signaturesOf(info: PackageInfo): List<ByteArray> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signing = info.signingInfo
            when {
                signing == null -> emptyList()
                signing.hasMultipleSigners() -> signing.apkContentsSigners.map { it.toByteArray() }
                else -> signing.signingCertificateHistory.map { it.toByteArray() }
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.map { it.toByteArray() } ?: emptyList()
        }

    private fun sameSignature(one: List<ByteArray>, other: List<ByteArray>): Boolean =
        one.any { first -> other.any { second -> first.contentEquals(second) } }
}
