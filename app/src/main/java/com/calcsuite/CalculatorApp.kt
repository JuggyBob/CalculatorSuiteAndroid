package com.calcsuite

import android.app.Application
import android.content.Context
import com.calcsuite.ai.AiConfigRepository
import com.calcsuite.data.CircuitRepository
import com.calcsuite.data.CurrencyRepository
import com.calcsuite.data.HistoryRepository
import com.calcsuite.data.SettingsRepository

/** Simple manual dependency container (no DI framework needed at this size). */
class AppContainer(context: Context) {
    val settings = SettingsRepository(context)
    val history = HistoryRepository(context)
    val currency = CurrencyRepository(context)

    /** The circuits that are being worked on, kept between visits. */
    val circuits = CircuitRepository(context)

    /** The AI feature: the provider, the model and the key. */
    val ai = AiConfigRepository(context)

    /**
     * A problem handed from one screen to another, such as from the AI
     * problem solver to the tool that would solve it.
     */
    val handoff = com.calcsuite.ui.tools.ToolHandoff()
}

class CalculatorApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
