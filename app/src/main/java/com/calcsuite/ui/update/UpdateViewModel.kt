package com.calcsuite.ui.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calcsuite.updater.ApkDownloader
import com.calcsuite.updater.ApkInstaller
import com.calcsuite.updater.AppVersion
import com.calcsuite.updater.DownloadFailure
import com.calcsuite.updater.GitHubReleaseClient
import com.calcsuite.updater.InstallCheck
import com.calcsuite.updater.ReleaseAsset
import com.calcsuite.updater.ReleaseCache
import com.calcsuite.updater.ReleaseInfo
import com.calcsuite.updater.UpdateCheck
import com.calcsuite.updater.UpdateService
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the updater is showing.
 *
 * The whole flow is one state, so that a check, a download and an install can
 * never be half-finished in the view: each of them replaces the state with
 * the one that follows it.
 */
sealed interface UpdateUiState {
    /** Nothing has been checked yet. */
    data object Idle : UpdateUiState

    /** The check is running. */
    data object Checking : UpdateUiState

    /** The installed version is the newest published one. */
    data class UpToDate(val installed: String, val release: ReleaseInfo?) : UpdateUiState

    /** A newer version is published and its package can be downloaded. */
    data class Available(val release: ReleaseInfo, val asset: ReleaseAsset) : UpdateUiState

    /** The package is being downloaded. */
    data class Downloading(
        val release: ReleaseInfo,
        val asset: ReleaseAsset,
        val written: Long,
        val total: Long,
    ) : UpdateUiState

    /** The package is on the phone and can be installed. */
    data class Ready(val release: ReleaseInfo, val file: File) : UpdateUiState

    /** A newer version is published, but no package can be chosen for it. */
    data class NoPackage(val release: ReleaseInfo) : UpdateUiState

    /** Something went wrong. The message says what. */
    data class Failed(val message: String) : UpdateUiState
}

/**
 * What should happen when the person asks to install the update.
 */
sealed interface InstallOutcome {
    /** The package may be installed: open the installer of Android. */
    data object Started : InstallOutcome

    /**
     * The app may not ask Android to install packages yet: the person has to
     * allow it in the settings of the phone first.
     */
    data object NeedsPermission : InstallOutcome

    /** The package is not one that may be installed. */
    data class Refused(val message: String) : InstallOutcome
}

/**
 * Drives the updater: it checks for a new release, downloads the package and
 * hands it to the installer of Android.
 *
 * The app never installs anything by itself. It downloads the published
 * package, checks that it is really a newer version of this app, and then
 * opens the installer, where Android asks the person to confirm.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val installer = ApkInstaller(application)
    private val service = UpdateService(GitHubReleaseClient())
    private val downloader = ApkDownloader(File(application.cacheDir, "updates"))
    private val cache = ReleaseCache(application)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var downloadJob: Job? = null

    /** The version of the app that is installed. */
    fun installedVersion(): String = installer.installed().versionName

    /** The version of the app that is installed, as a version to compare. */
    fun installedVersionNumber(): AppVersion? = installer.installed().version

    /**
     * Looks for a newer release.
     *
     * Only one check runs at a time: a second one does nothing, so that the
     * release API is not asked twice for the same thing.
     */
    fun check() {
        if (_state.value is UpdateUiState.Checking) return
        _state.value = UpdateUiState.Checking
        viewModelScope.launch {
            val installed = installer.installed()
            val version = installed.version
            if (version == null) {
                _state.value = UpdateUiState.Failed(
                    "The version of the installed app could not be read.",
                )
                return@launch
            }
            when (val result = service.check(version)) {
                is UpdateCheck.UpToDate -> {
                    _state.value = UpdateUiState.UpToDate(installed.versionName, result.release)
                    cache.save(result.release)
                }
                is UpdateCheck.Available -> {
                    _state.value = UpdateUiState.Available(result.release, result.asset)
                    cache.save(result.release)
                }
                is UpdateCheck.NoInstallableAsset -> {
                    _state.value = UpdateUiState.NoPackage(result.release)
                    cache.save(result.release)
                }
                is UpdateCheck.Unreadable -> {
                    _state.value = UpdateUiState.Failed(result.message)
                }
            }
        }
    }

    /**
     * Downloads the package of the release that was found.
     */
    fun download() {
        val available = _state.value as? UpdateUiState.Available ?: return
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            _state.value = UpdateUiState.Downloading(available.release, available.asset, 0L, 0L)
            try {
                val file = downloader.download(available.asset) { written, total ->
                    val current = _state.value
                    if (current is UpdateUiState.Downloading) {
                        _state.value = current.copy(written = written, total = total)
                    }
                }
                _state.value = UpdateUiState.Ready(available.release, file)
            } catch (e: DownloadFailure) {
                _state.value = UpdateUiState.Failed(e.message ?: "The download failed.")
            }
        }
    }

    /** Stops a download that is running. */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        val current = _state.value
        if (current is UpdateUiState.Downloading) {
            _state.value = UpdateUiState.Available(current.release, current.asset)
        }
    }

    /**
     * Checks the downloaded package and says whether it may be installed.
     *
     * Android checks the package again when it installs it, so this is only
     * to tell the person early, when something is wrong with the file.
     */
    fun checkDownloaded(file: File): InstallCheck = installer.check(file)

    /**
     * Checks the downloaded package and says what to do about it.
     */
    fun install(file: File): InstallOutcome = when (val check = checkDownloaded(file)) {
        is InstallCheck.Valid ->
            if (canRequestInstall()) InstallOutcome.Started else InstallOutcome.NeedsPermission
        is InstallCheck.WrongPackage -> InstallOutcome.Refused(
            "The downloaded file is for another app, so it was not opened.",
        )
        is InstallCheck.NotNewer -> InstallOutcome.Refused(
            "The downloaded file is not newer than the app that is installed, so it was not opened.",
        )
        is InstallCheck.WrongSignature -> InstallOutcome.Refused(
            "The downloaded file is signed with another certificate, so Android would refuse it. " +
                "It was not opened.",
        )
        is InstallCheck.Unreadable -> InstallOutcome.Refused(check.reason)
    }

    /** Says that something went wrong, so that the screen can show it. */
    fun reportProblem(message: String) {
        _state.value = UpdateUiState.Failed(message)
    }

    /** The intent that opens the installer for the downloaded package. */
    fun installIntent(file: File) = installer.installIntent(file)

    /** True when the app may ask Android to install a package. */
    fun canRequestInstall(): Boolean = installer.canRequestInstall()

    /** The intent that opens the screen where the permission is given. */
    fun unknownSourcesIntent() = installer.unknownSourcesIntent()

    /** The release that was read last, so the notes can be shown offline. */
    suspend fun cachedRelease(): ReleaseInfo? = cache.load()

    /** When the cached release was read. */
    suspend fun cachedAt(): Long? = cache.savedAt()

    /** Removes the files of earlier downloads. */
    fun clearDownloads() = downloader.clear()
}
