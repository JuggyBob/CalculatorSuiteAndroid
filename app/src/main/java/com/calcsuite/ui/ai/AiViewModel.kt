package com.calcsuite.ui.ai

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.calcsuite.ai.AiClient
import com.calcsuite.ai.AiConfigRepository
import com.calcsuite.ai.AiImage
import com.calcsuite.ai.AiImageLoader
import com.calcsuite.ai.AiPrompt
import com.calcsuite.ai.AiProblemSolver
import com.calcsuite.ai.AiProvider
import com.calcsuite.ai.AiSettings
import com.calcsuite.ai.tool.AiInterpreter
import com.calcsuite.ai.tool.AiInterpretation
import com.calcsuite.ai.tool.AiInterpretationStatus
import com.calcsuite.ai.tool.AiToolExecutor
import com.calcsuite.ai.tool.AiToolOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the AI screen is showing.
 *
 * The whole flow is one state, so that a change of screen or a rotation of
 * the phone cannot leave half of it behind: the picture, the reading of the
 * problem, the answer and the error all live here.
 */
data class AiUiState(
    /** True while a request is running. */
    val working: Boolean = false,
    /** What the screen is doing. */
    val stage: AiStage = AiStage.PICK,
    /** The picture the user chose, held while the picture is shown. */
    val image: AiImage? = null,
    /** Where the picture came from, so it can be shown again. */
    val imageUri: Uri? = null,
    /** The problem the model read. */
    val interpretation: AiInterpretation? = null,
    /** The answer the engine gave. */
    val outcome: AiToolOutcome? = null,
    /** The tool that was run. */
    val toolId: String? = null,
    /** The error to show, if there is one. */
    val error: String? = null,
)

/** The steps of the feature. */
enum class AiStage {
    /** Choosing or taking the picture. */
    PICK,

    /** The picture is shown and the question is being asked. */
    ANALYSING,

    /** The problem was read and the tool chosen; the answer is ready. */
    DONE,
}

/**
 * Drives the AI feature.
 *
 * The work is done off the main thread and is cancelled when the screen is
 * left, so a request that is no longer wanted does not go on costing money.
 */
class AiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AiConfigRepository(application)

    /** The way a picture becomes an answer. */
    private val solver = AiProblemSolver()

    private val _state = MutableStateFlow(AiUiState())
    val state: StateFlow<AiUiState> = _state.asStateFlow()

    /** The provider, the model and whether a key is saved. */
    val settings = repository.settings

    /** The settings as they are now, read when a request is made. */
    @Volatile
    private var currentSettings: AiSettings = AiSettings.DEFAULT

    init {
        viewModelScope.launch {
            repository.settings.collect { currentSettings = it }
        }
    }

    private var request: Job? = null

    /**
     * Takes the picture the user chose or photographed.
     */
    fun setImage(uri: Uri) {
        _state.update {
            it.copy(imageUri = uri, image = null, error = null, outcome = null, interpretation = null)
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { AiImageLoader.load(getApplication(), uri) }
            when (result) {
                is AiImageLoader.Result.Ready -> _state.update {
                    it.copy(image = result.image, stage = AiStage.PICK)
                }
                is AiImageLoader.Result.Failed -> _state.update {
                    it.copy(error = result.message)
                }
            }
        }
    }

    /** Throws the picture away so that another can be chosen. */
    fun clearImage() {
        request?.cancel()
        _state.value = AiUiState()
    }

    /**
     * Asks the provider to read the picture.
     *
     * The key is read, used and forgotten, so it is never held longer than
     * the request needs it.
     */
    fun analyse() {
        val picture = _state.value.image ?: return
        request?.cancel()
        _state.update { it.copy(working = true, stage = AiStage.ANALYSING, error = null) }
        request = viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val provider = currentSettings.provider
                val model = currentSettings.model
                val key = repository.apiKey(provider)
                if (key.isBlank() || model.isBlank()) {
                    return@withContext Result.Failure(
                        "Tell the AI feature which provider, model and key to use first.",
                    )
                }
                when (
                    val read = solver.interpret(provider, model, key, picture)
                ) {
                    is com.calcsuite.core.CalcResult.Err -> Result.Failure(read.error.message)
                    is com.calcsuite.core.CalcResult.Ok -> Result.Read(read.value)
                }
            }
            when (outcome) {
                is Result.Failure -> _state.update {
                    it.copy(working = false, error = outcome.message, stage = AiStage.PICK)
                }
                is Result.Read -> {
                    val interpretation = outcome.interpretation
                    if (interpretation.status == AiInterpretationStatus.READY) {
                        // The answer is worked out straight away when the
                        // reading is clear, and the user is shown what was
                        // read before it is used.
                        val answer = withContext(Dispatchers.IO) {
                            solver.solve(interpretation)
                        }
                        when (answer) {
                            is com.calcsuite.core.CalcResult.Err -> _state.update {
                                it.copy(
                                    working = false,
                                    error = answer.error.message,
                                    interpretation = interpretation,
                                    stage = AiStage.DONE,
                                )
                            }
                            is com.calcsuite.core.CalcResult.Ok -> _state.update {
                                it.copy(
                                    working = false,
                                    interpretation = interpretation,
                                    outcome = answer.value,
                                    toolId = interpretation.toolId,
                                    stage = AiStage.DONE,
                                )
                            }
                        }
                    } else {
                        _state.update {
                            it.copy(
                                working = false,
                                interpretation = interpretation,
                                stage = AiStage.DONE,
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Runs the tool again, with the arguments the user has corrected.
     *
     * The reading the model gave is kept, but the arguments that were
     * actually used are shown, so that what was worked out is always clear.
     */
    fun runAgain(toolId: String, arguments: Map<String, String>) {
        _state.update { it.copy(working = true, error = null) }
        request = viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                AiToolExecutor.run(toolId, arguments)
            }
            when (outcome) {
                is com.calcsuite.core.CalcResult.Err -> _state.update {
                    it.copy(working = false, error = outcome.error.message)
                }
                is com.calcsuite.core.CalcResult.Ok -> _state.update {
                    it.copy(
                        working = false,
                        outcome = outcome.value,
                        toolId = toolId,
                        interpretation = it.interpretation?.copy(arguments = arguments),
                    )
                }
            }
        }
    }

    /** What happened during a request. */
    private sealed interface Result {
        data class Read(val interpretation: AiInterpretation) : Result
        data class Failure(val message: String) : Result
    }

    /** Stops the request when the screen is left. */
    fun cancel() {
        request?.cancel()
        request = null
        _state.update { it.copy(working = false) }
    }

    override fun onCleared() {
        request?.cancel()
        super.onCleared()
    }
}
