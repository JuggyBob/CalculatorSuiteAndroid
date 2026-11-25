package com.calcsuite.ui.update

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calcsuite.ui.LocalApp
import com.calcsuite.updater.ReleaseCache
import com.calcsuite.updater.ReleaseInfo
import com.calcsuite.updater.SafeLinks
import com.calcsuite.updater.UpdatePromptPolicy
import com.calcsuite.updater.UpdatePromptPolicy.UpdatePromptState
import java.io.File
import kotlinx.coroutines.launch

/**
 * The one automatic check per app start and the release prompt that follows it.
 *
 * The gate is a sibling of the navigation content: it draws nothing until the
 * stored settings say onboarding is done, so the welcome pages are never
 * covered. It has no UI of its own — the prompt reuses [AboutDialog], which is
 * also what the Settings screen uses for the notes of a release.
 */
@Composable
fun UpdateGate() {
    val context = LocalContext.current
    val app = LocalApp.current
    val loaded by app.settings.settings.collectAsStateWithLifecycle(initialValue = null)

    // Self-gate: before this point there is no check and no dialog at all.
    if (loaded == null || loaded?.onboardingComplete != true) return

    val viewModel: UpdateViewModel = viewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val installedVersion = remember { viewModel.installedVersion() }
    val cache = remember { ReleaseCache(context.applicationContext) }
    var cachedRelease by remember { mutableStateOf<ReleaseInfo?>(null) }
    var suppressedTags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var cachedOnly by remember { mutableStateOf(false) }
    var dialogVisible by remember { mutableStateOf(false) }
    var installAfterDownload by remember { mutableStateOf(false) }
    var downloadAfterCheck by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    /** The existing install flow, shared by the automatic and manual actions. */
    fun startInstall(file: File) {
        when (val outcome = viewModel.install(file)) {
            InstallOutcome.Started -> try {
                // Android asks the person to confirm; the app cannot install
                // anything by itself.
                context.startActivity(viewModel.installIntent(file))
            } catch (e: Exception) {
                viewModel.reportProblem("The installer could not be opened on this phone.")
            }
            InstallOutcome.NeedsPermission -> try {
                context.startActivity(viewModel.unknownSourcesIntent())
            } catch (e: Exception) {
                viewModel.reportProblem("The settings screen of the phone could not be opened.")
            }
            is InstallOutcome.Refused -> viewModel.reportProblem(outcome.message)
        }
    }

    val candidate = candidateFor(state, cachedRelease, cachedOnly)
    val shouldPrompt = candidate != null && UpdatePromptPolicy.shouldPrompt(
        installedVersion = installedVersion,
        latestTag = candidate.release.tagName,
        suppressedTags = suppressedTags,
        state = candidate.phase,
    )

    // The dialog shows the live state whenever there is one — Failed included,
    // so a refusal or a failed download is never replaced by a make-believe
    // "available" card. Only Idle/Checking with a cached release are shaped
    // into a prompt (pure rule in UpdatePromptPolicy). Real progress and
    // errors pass through untouched, and the release shown below comes from
    // the current dialog state on every recomposition: no stale cached notes
    // after a live check has produced a fresh release.
    val cachedCandidate = candidate?.takeIf { it.fromCache }?.let {
        UpdatePromptPolicy.CachedCandidate(it.release)
    }
    val dialogState = UpdatePromptPolicy.dialogStateFor(state, cachedCandidate)
    val fromCache = (state is UpdateUiState.Idle || state is UpdateUiState.Checking) &&
        cachedCandidate != null
    val dialogRelease: ReleaseInfo? = when (val visible = dialogState) {
        is UpdateUiState.Available -> visible.release
        is UpdateUiState.Downloading -> visible.release
        is UpdateUiState.Ready -> visible.release
        is UpdateUiState.NoPackage -> visible.release
        is UpdateUiState.UpToDate -> visible.release
        else -> if (fromCache) cachedCandidate?.release else null
    }

    LaunchedEffect(Unit) {
        val stored = runCatching { viewModel.cachedRelease() }.getOrNull()
        if (stored != null) cachedRelease = stored
        suppressedTags = runCatching { cache.suppressedTags() }.getOrDefault(emptySet())

        val now = System.currentTimeMillis()
        val last = runCatching { cache.lastAutoCheckAt() }.getOrNull()
        if (!UpdatePromptPolicy.shouldAutoCheck(last, now)) {
            cachedOnly = true
            return@LaunchedEffect
        }
        if (!isOnline(context)) {
            cachedOnly = true
            return@LaunchedEffect
        }
        cachedOnly = false
        // Mark first: a failed or slow check must not be retried on every
        // restart. A failed automatic check never opens a dialog on its own;
        // if the prompt is already on screen it shows the error in place.
        runCatching { cache.markAutoChecked(now) }
        runCatching { viewModel.check() }
    }

    LaunchedEffect(state) {
        when (val current = state) {
            is UpdateUiState.Available -> if (downloadAfterCheck) {
                downloadAfterCheck = false
                installAfterDownload = true
                viewModel.download()
            }
            is UpdateUiState.Ready -> if (installAfterDownload) {
                installAfterDownload = false
                startInstall(current.file)
            }
            is UpdateUiState.Failed, is UpdateUiState.UpToDate, is UpdateUiState.NoPackage -> {
                installAfterDownload = false
                downloadAfterCheck = false
            }
            else -> Unit
        }
    }

    // A dismissal is decided by the person, not by the next state change: once
    // shown, the dialog stays until Close, back, or "Never show again". Its
    // contents are derived from the current state, so an error replaces the
    // prompt in place rather than being swallowed by a stale cached card.
    LaunchedEffect(shouldPrompt) {
        if (shouldPrompt) dialogVisible = true
    }

    if (dialogVisible) {
        AboutDialog(
            installedVersion = installedVersion,
            release = dialogRelease,
            fromCache = fromCache,
            state = dialogState,
            downloadLabel = "Download & install now",
            onCheck = { viewModel.check() },
            onDownload = {
                if (dialogState is UpdateUiState.Available) {
                    installAfterDownload = true
                    if (state is UpdateUiState.Available) {
                        viewModel.download()
                    } else {
                        // Cached release: refresh it at the person's request,
                        // then continue into the same download → install flow.
                        downloadAfterCheck = true
                        viewModel.check()
                    }
                }
            },
            onCancel = {
                installAfterDownload = false
                downloadAfterCheck = false
                viewModel.cancelDownload()
            },
            onInstall = {
                val ready = dialogState
                if (ready is UpdateUiState.Ready) startInstall(ready.file)
            },
            onOpenReleasePage = { url -> openPage(context, url) },
            onDismiss = { dialogVisible = false },
            onNeverShowAgain = (dialogRelease?.tagName ?: candidate?.release?.tagName)
                ?.trim()?.takeIf { it.isNotEmpty() }?.let { tag ->
                {
                    scope.launch { runCatching { cache.suppress(tag) } }
                    suppressedTags = UpdatePromptPolicy.suppressionAfterNeverShow(tag, suppressedTags)
                    dialogVisible = false
                }
            },
        )
    }
}

/** The release the prompt would be about, and why it is eligible. */
private data class PromptCandidate(
    val release: ReleaseInfo,
    val phase: UpdatePromptState,
    val fromCache: Boolean,
)

/** Prefers a release from a check; falls back to the cached one when offline. */
private fun candidateFor(
    state: UpdateUiState,
    cached: ReleaseInfo?,
    cachedOnly: Boolean,
): PromptCandidate? = when (state) {
    is UpdateUiState.Available -> PromptCandidate(state.release, UpdatePromptState.Available, false)
    is UpdateUiState.UpToDate -> state.release?.let {
        PromptCandidate(it, UpdatePromptState.UpToDate, false)
    }
    is UpdateUiState.NoPackage -> PromptCandidate(state.release, UpdatePromptState.NoPackage, false)
    else -> if (cachedOnly && cached != null) {
        val phase = if (cached.preferredApk() != null) {
            UpdatePromptState.Available
        } else {
            UpdatePromptState.NoPackage
        }
        PromptCandidate(cached, phase, true)
    } else {
        null
    }
}

/** True when the phone has a usable connection for an automatic check. */
private fun isOnline(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/** Opens the release page in whatever the phone uses for web pages. */
private fun openPage(context: Context, address: String) {
    // An implicit ACTION_VIEW must not be handed an address from release
    // metadata unless it is an explicit https link to github.com.
    val safe = SafeLinks.releasePageUrl(address) ?: return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(safe)))
    } catch (e: Exception) {
        // There is nothing on the phone that can show a web page.
    }
}
