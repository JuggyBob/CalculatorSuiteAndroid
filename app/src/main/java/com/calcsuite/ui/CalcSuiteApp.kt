package com.calcsuite.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.calcsuite.AppContainer
import com.calcsuite.data.HistoryEntry
import com.calcsuite.data.Settings
import com.calcsuite.data.ToolRegistry
import com.calcsuite.expr.AngleMode
import com.calcsuite.ui.components.ProvideSettings
import com.calcsuite.ui.screens.CategoryScreen
import com.calcsuite.ui.screens.HistoryScreen
import com.calcsuite.ui.screens.MainScreen
import com.calcsuite.ui.screens.OnboardingScreen
import com.calcsuite.ui.screens.SearchScreen
import com.calcsuite.ui.screens.SettingsScreen
import com.calcsuite.ui.screens.ScientificScreen
import com.calcsuite.ui.screens.ToolHostScreen
import com.calcsuite.ui.theme.CalcSuiteTheme
import com.calcsuite.ui.theme.ThemeMode
import kotlinx.coroutines.launch

val LocalApp = compositionLocalOf<AppContainer> { error("AppContainer is not available") }

object Routes {
    const val MAIN = "main"
    const val SCIENTIFIC = "scientific"
    const val SEARCH = "search"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val CATEGORY = "category/{categoryId}"
    const val TOOL = "tool/{toolId}"

    fun category(id: String) = "category/$id"
    fun tool(id: String) = "tool/$id"
}

@Composable
fun CalcSuiteApp(container: AppContainer, initialSettings: Settings) {
    // Null until the stored preferences have been read, so the welcome pages
    // never flash for someone who has already been through them.
    val loaded by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val settings = loaded ?: initialSettings
    val favorites by container.settings.favorites.collectAsStateWithLifecycle(emptySet())
    val recents by container.settings.recents.collectAsStateWithLifecycle(emptyList())
    val history by container.history.history.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()

    fun record(toolId: String, toolTitle: String, category: String, input: String, result: String) {
        scope.launch {
            container.history.add(
                HistoryEntry(
                    id = System.currentTimeMillis() * 1000 + (0..999).random(),
                    toolId = toolId,
                    toolTitle = toolTitle,
                    category = category,
                    input = input,
                    result = result,
                    timestamp = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun openTool(id: String) {
        scope.launch { container.settings.markRecent(id) }
        nav.navigate(Routes.tool(id))
    }

    fun cycleAngle() {
        scope.launch {
            container.settings.setAngleMode(
                when (settings.angleMode) {
                    AngleMode.DEG -> AngleMode.RAD
                    AngleMode.RAD -> AngleMode.GRAD
                    AngleMode.GRAD -> AngleMode.DEG
                },
            )
        }
    }

    CalcSuiteTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
        ProvideSettings(settings) {
            // Opaque, theme-aware backdrop for every destination. Screens that
            // are transparent (or leave padding gaps) must never expose the
            // default light baseline surface.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                // First launch: welcome pages instead of the main menu. The main
                // menu itself is untouched — it appears as soon as onboarding is
                // finished or skipped, and never again after that.
                when {
                    loaded == null -> Unit
                    !settings.onboardingComplete -> OnboardingScreen(
                        onFinish = { scope.launch { container.settings.setOnboardingComplete(true) } },
                    )

                    else -> NavHost(navController = nav, startDestination = Routes.MAIN) {
                composable(Routes.MAIN) {
                    MainScreen(
                        favorites = favorites,
                        recents = recents,
                        onOpenTool = ::openTool,
                        onOpenCategory = { nav.navigate(Routes.category(it.id)) },
                        onOpenSearch = { nav.navigate(Routes.SEARCH) },
                        onOpenHistory = { nav.navigate(Routes.HISTORY) },
                        onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                        onOpenScientific = { nav.navigate(Routes.SCIENTIFIC) },
                        onCycleAngle = ::cycleAngle,
                        onRecord = { expr, result ->
                            record("calc.standard", "Calculator", "Calculator", expr, result)
                        },
                    )
                }
                composable(Routes.SCIENTIFIC) {
                    ScientificScreen(
                        onBack = { nav.popBackStack() },
                        onCycleAngle = ::cycleAngle,
                        onOpenHistory = { nav.navigate(Routes.HISTORY) },
                        onRecord = { expr, result ->
                            record("calc.scientific", "Scientific calculator", "Calculator", expr, result)
                        },
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        favorites = favorites,
                        onBack = { nav.popBackStack() },
                        onOpenTool = ::openTool,
                    )
                }
                composable(Routes.HISTORY) {
                    HistoryScreen(
                        history = history,
                        onBack = { nav.popBackStack() },
                        onOpenTool = ::openTool,
                        onDelete = { id -> scope.launch { container.history.remove(id) } },
                        onClearAll = { scope.launch { container.history.clear() } },
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        settings = settings,
                        historyCount = history.size,
                        onBack = { nav.popBackStack() },
                        onSetTheme = { scope.launch { container.settings.setThemeMode(it) } },
                        onSetAngle = { scope.launch { container.settings.setAngleMode(it) } },
                        onSetPrecision = { scope.launch { container.settings.setPrecision(it) } },
                        onSetGrouping = { scope.launch { container.settings.setGrouping(it) } },
                        onSetThousandsSeparator = {
                            scope.launch { container.settings.setThousandsSeparator(it) }
                        },
                        onSetHaptics = { scope.launch { container.settings.setHaptics(it) } },
                        onSetDynamicColor = { scope.launch { container.settings.setDynamicColor(it) } },
                        onClearHistory = { scope.launch { container.history.clear() } },
                    )
                }
                composable(
                    Routes.CATEGORY,
                    arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
                ) { entry ->
                    val categoryId = entry.arguments?.getString("categoryId").orEmpty()
                    CategoryScreen(
                        categoryId = categoryId,
                        favorites = favorites,
                        onBack = { nav.popBackStack() },
                        onOpenTool = ::openTool,
                        onToggleFavorite = { id -> scope.launch { container.settings.toggleFavorite(id) } },
                    )
                }
                composable(
                    Routes.TOOL,
                    arguments = listOf(navArgument("toolId") { type = NavType.StringType }),
                ) { entry ->
                    val toolId = entry.arguments?.getString("toolId").orEmpty()
                    val tool = ToolRegistry.byId(toolId)
                    if (tool == null) {
                        nav.popBackStack()
                    } else {
                        ToolHostScreen(
                            tool = tool,
                            favorite = toolId in favorites,
                            onBack = { nav.popBackStack() },
                            onToggleFavorite = {
                                scope.launch { container.settings.toggleFavorite(toolId) }
                            },
                            onRecord = { input, result ->
                                record(tool.id, tool.title, tool.category.label, input, result)
                            },
                            onOpenTool = ::openTool,
                        )
                    }
                }
                    }
                }
            }
        }
    }
}
