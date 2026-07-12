package com.networkscanner.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.networkscanner.app.ui.components.ValueBadge

@Composable
fun SettingsCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp)
    )
}

@Composable
private fun SettingLeadingIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.size(24.dp)
    )
}

@Composable
fun SwitchSettingItem(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = icon?.let { { SettingLeadingIcon(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled
            )
        },
        modifier = Modifier.toggleable(
            value = checked,
            onValueChange = onCheckedChange,
            role = Role.Switch,
            enabled = enabled
        )
    )
}

@Composable
fun ClickableSettingItem(
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = icon?.let { { SettingLeadingIcon(it) } },
        trailingContent = value?.let { { ValueBadge(text = it) } },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick)
    )
}
