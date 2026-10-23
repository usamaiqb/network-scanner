package com.networkscanner.app.ui.expressive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Label above a [SegmentedGroup]. labelMedium bold in `primary`, inset 12.dp so it hangs
 * just left of the group's 24.dp corner rather than lining up with the row text.
 *
 * [count] renders a trailing badge; pass null (the default) to omit it.
 * [color] exists for the one legitimate exception: a destructive or debug section in `error`.
 *
 * [contentDescription] collapses the header into a single accessibility node reading whatever
 * the caller supplies. Worth passing whenever [count] is set: unlabelled, a screen reader
 * announces the title and the bare number as two unrelated nodes ("Online", "7"), which says
 * nothing about what was counted. The string has to come from the caller because only the
 * caller knows the unit and can localise it - network-scanner passes
 * `stringResource(R.string.cd_section_header, title, count)`.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String? = null
) {
    Row(
        modifier = modifier
            .padding(start = 12.dp, bottom = 8.dp)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics(mergeDescendants = true) {
                        this.contentDescription = contentDescription
                    }
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        if (count != null) {
            Badge(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text(text = count.toString(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
