package com.networkscanner.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

private val SegmentRadius = 24.dp
private val SegmentFlatRadius = 4.dp

/**
 * Returns the appropriate M3 Expressive segment shape for an item at [index] in a list of [count] items.
 * Uses 4dp on flat edges (not 0dp) for the subtle rounded feel per M3 Expressive guidelines.
 */
fun segmentShape(index: Int, count: Int): Shape {
    return when {
        count == 1 -> RoundedCornerShape(SegmentRadius)
        index == 0 -> RoundedCornerShape(
            topStart = SegmentRadius, topEnd = SegmentRadius,
            bottomStart = SegmentFlatRadius, bottomEnd = SegmentFlatRadius
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = SegmentFlatRadius, topEnd = SegmentFlatRadius,
            bottomStart = SegmentRadius, bottomEnd = SegmentRadius
        )
        else -> RoundedCornerShape(SegmentFlatRadius)
    }
}

/**
 * A single segment surface with proper M3 Expressive shape.
 */
@Composable
fun SegmentSurface(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        shape = segmentShape(index, count),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
        content = content
    )
}

/**
 * A vertically stacked segmented list where each item gets its own surface segment,
 * following the M3 Expressive grouped list pattern.
 */
@Composable
fun <T> SegmentedListColumn(
    items: List<T>,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, shape: Shape) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            val shape = segmentShape(index, items.size)
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                itemContent(item, shape)
            }
            if (index < items.size - 1) {
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}
