package com.calcsuite.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calcsuite.data.ToolDef
import com.calcsuite.ui.components.ToolScaffold
import com.calcsuite.ui.tools.ToolArgs
import com.calcsuite.ui.tools.advanced.AdvancedTools
import com.calcsuite.ui.tools.algebra.AlgebraTools
import com.calcsuite.ui.tools.calculus.CalculusTools
import com.calcsuite.ui.tools.convert.ConvertTools
import com.calcsuite.ui.tools.datetime.DateTimeTools
import com.calcsuite.ui.tools.finance.FinanceTools
import com.calcsuite.ui.tools.geometry.GeometryTools
import com.calcsuite.ui.tools.health.HealthTools
import com.calcsuite.ui.tools.other.OtherTools

/**
 * Host for every tool screen. The mapping below is the single place where a
 * tool id becomes a screen, which keeps navigation, favourites and history
 * consistent across all 48 tools.
 */
@Composable
fun ToolHostScreen(
    tool: ToolDef,
    favorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRecord: (String, String) -> Unit,
    onOpenTool: (String) -> Unit,
) {
    val args = remember(tool, favorite) {
        ToolArgs(
            tool = tool,
            favorite = favorite,
            onBack = onBack,
            onToggleFavorite = onToggleFavorite,
            onRecord = onRecord,
            onOpenTool = onOpenTool,
        )
    }
    ToolScreenDispatch(args)
}

/**
 * Each batch dispatcher handles the ids of its own domain and returns false
 * for every other id, so the chain resolves exactly one screen per tool.
 * Together the nine branches cover every registered tool.
 */
@Composable
private fun ToolScreenDispatch(args: ToolArgs) {
    when {
        AlgebraTools(args) -> Unit
        CalculusTools(args) -> Unit
        AdvancedTools(args) -> Unit
        GeometryTools(args) -> Unit
        ConvertTools(args) -> Unit
        FinanceTools(args) -> Unit
        HealthTools(args) -> Unit
        DateTimeTools(args) -> Unit
        OtherTools(args) -> Unit
        else -> MissingToolScreen(args)
    }
}

/**
 * Defensive fallback for a registry/dispatch mismatch. Unreachable while the
 * registry and the dispatch chain agree; logs loudly if it ever renders.
 */
@Composable
private fun MissingToolScreen(args: ToolArgs) {
    SideEffect { Log.e("ToolHostScreen", "No screen registered for tool id '${args.tool.id}'") }
    ToolScaffold(
        title = args.tool.title,
        subtitle = args.tool.description,
        onBack = args.onBack,
        favorite = args.favorite,
        onToggleFavorite = args.onToggleFavorite,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Tool unavailable", style = MaterialTheme.typography.titleMedium)
            Text(
                "No screen is registered for \"${args.tool.id}\" in this build.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
