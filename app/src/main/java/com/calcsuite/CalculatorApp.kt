package com.calcsuite

import android.app.Application
import android.content.Context
import com.calcsuite.data.CurrencyRepository
import com.calcsuite.data.HistoryRepository
import com.calcsuite.data.SettingsRepository

/** Simple manual dependency container (no DI framework needed at this size). */
class AppContainer(context: Context) {
    val settings = SettingsRepository(context)
    val history = HistoryRepository(context)
    val currency = CurrencyRepository(context)
}

class CalculatorApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
