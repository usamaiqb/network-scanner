package com.networkscanner.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.networkscanner.app.ui.components.SectionHeader
import com.networkscanner.app.ui.components.SegmentSurface

/**
 * A single row inside a [DetailSection] or [DetailRows].
 *
 * Rows are collected into a list before rendering so the section header count and the
 * segment shapes both derive from the same list, rather than from offsets kept in sync
 * by hand as conditional rows are added and removed.
 */
data class DetailRow(
    val content: @Composable () -> Unit
)

/**
 * Builder for a section's rows. An absent optional value simply doesn't add a row, which
 * is what keeps missing data out of the UI instead of rendering an "Unknown" placeholder.
 */
class DetailRowScope internal constructor() {
    internal val rows = mutableListOf<DetailRow>()

    /** Adds a label/value row. */
    fun row(label: String, value: String) {
        rows += DetailRow { InfoRow(label = label, value = value) }
    }

    /** Adds a label/value row only when [value] is non-null and not blank. */
    fun rowIfPresent(label: String, value: String?) {
        value?.takeIf { it.isNotBlank() }?.let { row(label, it) }
    }

    /** Adds an arbitrary composable row. */
    fun custom(content: @Composable () -> Unit) {
        rows += DetailRow(content)
    }
}

/**
 * Renders a titled section of segmented rows, or nothing at all when no rows were added.
 */
@Composable
fun DetailSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable DetailRowScope.() -> Unit
) {
    val scope = DetailRowScope()
    scope.content()
    val rows = scope.rows
    if (rows.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = title, count = rows.size)
        Spacer(Modifier.height(8.dp))
        SegmentedRows(rows)
    }
}

/**
 * Renders segmented rows without a header, for callers that draw their own.
 */
@Composable
fun DetailRows(
    modifier: Modifier = Modifier,
    content: @Composable DetailRowScope.() -> Unit
) {
    val scope = DetailRowScope()
    scope.content()
    if (scope.rows.isEmpty()) return
    SegmentedRows(scope.rows, modifier)
}

@Composable
private fun SegmentedRows(
    rows: List<DetailRow>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        rows.forEachIndexed { index, row ->
            SegmentSurface(index = index, count = rows.size) {
                row.content()
            }
        }
    }
}
