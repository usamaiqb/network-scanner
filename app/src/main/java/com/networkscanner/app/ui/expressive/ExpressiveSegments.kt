package com.networkscanner.app.ui.expressive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive "connected pill" grouped-list shapes: items in a group share
 * tight inner corners while the first and last get large outer corners, separated by a
 * small gap instead of dividers. This is the signature look of the polish playbook.
 *
 * Canonical spec (UI-POLISH-PLAYBOOK.md):
 *  - outer radius 24.dp, inner radius 4.dp - 4, not 0; the subtle rounding *is* the look
 *  - 2.dp gap between rows, never a divider; 20.dp between groups
 *  - row container `surfaceContainer` on a `surfaceContainerLowest` screen background
 */
enum class SegmentPosition { Single, First, Middle, Last }

val GroupOuterRadius = 24.dp
val GroupInnerRadius = 4.dp
val GroupGap = 2.dp

/**
 * Gap between one group (header + rows) and the next. Ten times [GroupGap], which is what
 * makes the 2.dp inner gap read as "these rows belong together" rather than as a mistake.
 */
val SectionGap = 20.dp

fun segmentShape(position: SegmentPosition): Shape = when (position) {
    SegmentPosition.Single -> RoundedCornerShape(GroupOuterRadius)
    SegmentPosition.First -> RoundedCornerShape(
        topStart = GroupOuterRadius, topEnd = GroupOuterRadius,
        bottomStart = GroupInnerRadius, bottomEnd = GroupInnerRadius
    )
    SegmentPosition.Middle -> RoundedCornerShape(GroupInnerRadius)
    SegmentPosition.Last -> RoundedCornerShape(
        topStart = GroupInnerRadius, topEnd = GroupInnerRadius,
        bottomStart = GroupOuterRadius, bottomEnd = GroupOuterRadius
    )
}

fun segmentPositionFor(index: Int, count: Int): SegmentPosition = when {
    count <= 1 -> SegmentPosition.Single
    index == 0 -> SegmentPosition.First
    index == count - 1 -> SegmentPosition.Last
    else -> SegmentPosition.Middle
}

/** Index-based overload, for LazyColumn / forEachIndexed call sites. */
fun segmentShape(index: Int, count: Int): Shape = segmentShape(segmentPositionFor(index, count))

/**
 * Container for a run of connected rows. Supplies the 2.dp inter-row gap so callers
 * never repeat it; each child still owns its own [segmentShape] via its position.
 * Use this for a fixed, hand-written sequence of rows; use [SegmentedListColumn] when
 * the row count is data-driven.
 */
@Composable
fun SegmentedGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GroupGap)
    ) {
        content()
    }
}

/** A single row surface carrying the connected-pill shape for its [position]. */
@Composable
fun SegmentSurface(
    position: SegmentPosition,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = segmentShape(position),
        color = color,
        content = content
    )
}

/**
 * Index-based overload. Present because a row's position is usually derived from a loop, and
 * `SegmentSurface(index = i, count = n)` at the call site is less noise than wrapping every
 * one in `segmentPositionFor(...)`. Mirrors the [segmentShape] overload pair.
 */
@Composable
fun SegmentSurface(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    SegmentSurface(
        position = segmentPositionFor(index, count),
        modifier = modifier,
        color = color,
        content = content
    )
}

/** Data-driven connected group. [itemContent] receives the resolved shape for clipping. */
@Composable
fun <T> SegmentedListColumn(
    items: List<T>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    itemContent: @Composable (item: T, shape: Shape) -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GroupGap)
    ) {
        items.forEachIndexed { index, item ->
            val shape = segmentShape(segmentPositionFor(index, items.size))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = shape,
                color = color
            ) {
                itemContent(item, shape)
            }
        }
    }
}
