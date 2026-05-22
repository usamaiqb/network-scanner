package com.networkscanner.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.networkscanner.app.R
import com.networkscanner.app.data.repository.CustomPortData
import com.networkscanner.app.ui.components.SegmentSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPortsScreen(
    ports: List<CustomPortData>,
    onNavigateBack: () -> Unit,
    onAddPort: (Int, String) -> Unit,
    onDeletePort: (Int) -> Unit,
    onTogglePort: (Int, Boolean) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.custom_ports)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_port)) }
            )
        }
    ) { padding ->
        if (ports.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.no_custom_ports),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.tap_add_to_create),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(ports, key = { it.id }) { port ->
                    SegmentSurface(
                        index = ports.indexOf(port),
                        count = ports.size
                    ) {
                        CustomPortItem(
                            port = port,
                            onToggle = { onTogglePort(port.id, !port.isEnabled) },
                            onDelete = { onDeletePort(port.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddPortDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { portNumber, serviceName ->
                onAddPort(portNumber, serviceName)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun CustomPortItem(
    port: CustomPortData,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${port.port}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = port.serviceName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = port.isEnabled,
                onCheckedChange = { onToggle() }
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_port),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AddPortDialog(
    onDismiss: () -> Unit,
    onAdd: (Int, String) -> Unit
) {
    var portNumber by remember { mutableStateOf("") }
    var serviceName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_custom_port)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = portNumber,
                    onValueChange = {
                        portNumber = it.filter { c -> c.isDigit() }
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.port_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null
                )
                OutlinedTextField(
                    value = serviceName,
                    onValueChange = { serviceName = it },
                    label = { Text(stringResource(R.string.service_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("HTTP, SSH, Ollama...") }
                )
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val port = portNumber.toIntOrNull()
                    when {
                        port == null || port !in 1..65535 ->
                            errorMessage = "Port must be between 1 and 65535"
                        serviceName.isBlank() ->
                            errorMessage = "Service name is required"
                        else ->
                            onAdd(port, serviceName.trim())
                    }
                }
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
