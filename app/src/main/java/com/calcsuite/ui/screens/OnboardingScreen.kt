package com.calcsuite.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.calcsuite.data.CalculusIcon
import com.calcsuite.data.ToolCategory
import com.calcsuite.data.ToolRegistry
import kotlinx.coroutines.launch

/**
 * One welcome page.
 *
 * [illustration] is drawn above the copy; [bullets] is the short list of what
 * the page is about, rendered as a small card stack.
 */
private data class WelcomePage(
    val eyebrow: String,
    val title: String,
    val body: String,
    val illustration: ImageVector?,
    val illustrationCaption: String?,
    val bullets: List<String>,
)

/**
 * First-launch welcome experience.
 *
 * Shown once, before the main menu, by [CalcSuiteApp]. Reaching the last page
 * or pressing Skip both invoke [onFinish], which persists the choice, so the
 * pages never appear again.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pages = rememberWelcomePages()
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == pages.lastIndex

    fun finish() = onFinish()

    fun goNext() {
        if (lastPage) finish() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
    }

    fun goBack() {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        } else {
            // Back on the first page means the same thing as Skip.
            finish()
        }
    }

    // Back walks the pages backwards, and leaves onboarding on the first page
    // instead of dropping out of the app.
    BackHandler(onBack = ::goBack)

    // Short viewports (or a large system font) get a scrollable page body so the
    // footer controls stay reachable.
    val configuration = LocalConfiguration.current
    val shortViewport = configuration.screenHeightDp < 640

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        // ------------------------------------------------------------- top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                pages.getOrNull(pagerState.currentPage)?.eyebrow.orEmpty(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(visible = !lastPage, enter = fadeIn(), exit = fadeOut()) {
                TextButton(onClick = ::finish) { Text("Skip") }
            }
        }

        // -------------------------------------------------------------- pages
        Box(modifier = Modifier.weight(1f)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 8.dp,
                // Keeps the sliding motion visible without needing a long page.
                beyondViewportPageCount = 0,
            ) { index ->
                WelcomePageContent(
                    page = pages[index],
                    compact = shortViewport,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // ------------------------------------------------------------- footer
        PageIndicator(
            pageCount = pages.size,
            current = pagerState.currentPage,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(visible = pagerState.currentPage > 0, enter = fadeIn(), exit = fadeOut()) {
                OutlinedButton(onClick = ::goBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Previous")
                }
            }
            Button(
                onClick = ::goNext,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) {
                Text(if (lastPage) "Get started" else "Next")
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun WelcomePageContent(
    page: WelcomePage,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val illustrationSize by animateDpAsState(if (compact) 132.dp else 176.dp, label = "hero")

    Column(
        modifier = modifier
            .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp),
    ) {
        HeroBadge(
            icon = page.illustration,
            caption = page.illustrationCaption,
            size = illustrationSize,
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                page.title,
                style = if (compact) MaterialTheme.typography.headlineSmall
                else MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                page.body,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (page.bullets.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    page.bullets.forEach { line -> BulletLine(line) }
                }
            }
        }
    }
}

/** Rounded tile holding the page's glyph, or its caption when there is no glyph. */
@Composable
private fun HeroBadge(
    icon: ImageVector?,
    caption: String?,
    size: Dp,
) {
    Surface(
        modifier = Modifier.size(size),
        shape = RoundedCornerShape(percent = 28),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(size * 0.46f),
                )
            } else if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun BulletLine(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** "Page 2 of 4" told both visually (dots) and in words for screen readers. */
@Composable
private fun PageIndicator(pageCount: Int, current: Int, modifier: Modifier = Modifier) {
    val label = "Page ${current + 1} of $pageCount"
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            repeat(pageCount) { index ->
                val selected = index == current
                val color by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    animationSpec = tween(200),
                    label = "dot",
                )
                val width by animateDpAsState(
                    targetValue = if (selected) 22.dp else 7.dp,
                    animationSpec = tween(200),
                    label = "dotWidth",
                )
                Box(
                    Modifier
                        .height(7.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }
    }
}

private fun rememberWelcomePages(): List<WelcomePage> {
    val toolCount = ToolRegistry.all.size
    return listOf(
        WelcomePage(
            eyebrow = "Welcome",
            title = "Every calculator, one app",
            body = "Quick arithmetic when you need it, and $toolCount purpose-built tools " +
                "for the jobs a phone keypad cannot do.",
            illustration = null,
            illustrationCaption = "=",
            bullets = listOf(
                "A fast everyday calculator with a live preview",
                "$toolCount specialist tools you can search by name or keyword",
                "Saved history so you can find yesterday's work",
            ),
        ),
        WelcomePage(
            eyebrow = "Organised",
            title = "Seven categories",
            body = "Everything sits in a category, and search finds it by what you " +
                "remember — \"mortgage\", \"kcal\", \"octal\", \"tip\".",
            illustration = Icons.Filled.GridView,
            illustrationCaption = null,
            bullets = ToolCategory.entries.map { category ->
                val count = ToolRegistry.all.count { it.category == category }
                "${category.label} — ${if (count == 1) "1 tool" else "$count tools"}"
            },
        ),
        WelcomePage(
            eyebrow = "Mathematics",
            title = "A full calculus workbench",
            body = "Symbolic derivatives, antiderivatives, limits, series and Taylor " +
                "expansions — each one showing its working, not just an answer.",
            illustration = CalculusIcon,
            illustrationCaption = null,
            bullets = listOf(
                "Symbolic and numerical differentiation, partials included",
                "Indefinite and definite integration, with exact results",
                "Limits, infinite series and convergence, Σ and Π notation",
                "Taylor and Maclaurin expansions, roots and extrema",
                "An interactive graph you can zoom and pan, with f′ and areas",
            ),
        ),
        WelcomePage(
            eyebrow = "Your way",
            title = "Make it yours",
            body = "Choose how numbers are written, how much precision you see and " +
                "whether the app follows your system theme.",
            illustration = Icons.Filled.Tune,
            illustrationCaption = null,
            bullets = listOf(
                "Thousands separator: automatic, comma, period, space or apostrophe",
                "Result precision from 3 to 15 significant digits",
                "Degrees, radians or gradians as your default",
                "Light, dark or your wallpaper's colours",
                "Calculations stay on your device",
            ),
        ),
    )
}
