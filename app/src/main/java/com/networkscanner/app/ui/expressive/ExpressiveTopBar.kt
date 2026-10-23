package com.networkscanner.app.ui.expressive

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * The collapsing large top bar used on every polished screen.
 *
 * Two things the caller must still do, because they cannot be done from in here:
 *  1. `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` on the Scaffold
 *  2. `containerColor = MaterialTheme.colorScheme.surfaceContainerLowest` on the Scaffold,
 *     so the bar and the screen behind it share a ground
 *
 * ```
 * val scrollBehavior = rememberExpressiveScrollBehavior()
 * Scaffold(
 *     modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
 *     containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
 *     topBar = {
 *         CollapsingLargeTopBar("Settings", scrollBehavior, onNavigateBack = onBack)
 *     }
 * ) { padding -> ... }
 * ```
 *
 * The title is start-aligned: a centred title in a large bar has nowhere to travel as the
 * bar collapses, which is the whole point of the pattern.
 *
 * The title ellipsises rather than wrapping. A large bar's title is often data rather than a
 * literal - network-scanner puts the device's own name there, which can be a full hostname -
 * and an unbounded title pushes the bar's height around as the user navigates. Raise
 * [titleMaxLines] to 2 for a screen whose titles are genuinely long by design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberExpressiveScrollBehavior(): TopAppBarScrollBehavior =
    TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsingLargeTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    navigateBackDescription: String = "Back",
    titleMaxLines: Int = 1,
    actions: @Composable () -> Unit = {}
) {
    LargeTopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = navigateBackDescription
                    )
                }
            }
        },
        actions = { actions() },
        // topAppBarColors, not largeTopAppBarColors: the size-specific factories were
        // deprecated in Material3 1.4 and all collapse onto this one.
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        scrollBehavior = scrollBehavior
    )
}
