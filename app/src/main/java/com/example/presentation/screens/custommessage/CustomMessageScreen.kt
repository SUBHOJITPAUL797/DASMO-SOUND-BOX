package com.example.presentation.screens.custommessage

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomMessageScreen(
    viewModel: CustomMessageViewModel = viewModel(),
    onBack: () -> Unit
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()

    var prefixText by remember { mutableStateOf("") }
    var suffixText by remember { mutableStateOf("") }
    var isPrefixLoaded by remember { mutableStateOf(false) }
    var isSuffixLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(settings.customPrefix) {
        if (!isPrefixLoaded) {
            prefixText = settings.customPrefix
            isPrefixLoaded = true
        }
    }

    LaunchedEffect(settings.customSuffix) {
        if (!isSuffixLoaded) {
            suffixText = settings.customSuffix
            isSuffixLoaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom Messages") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Prefix (Spoken before amount)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = settings.customPrefixEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.updatePrefix(enabled, prefixText)
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text(if (settings.customPrefixEnabled) "Enabled" else "Disabled")
            }
            if (settings.customPrefixEnabled) {
                OutlinedTextField(
                    value = prefixText,
                    onValueChange = { newText ->
                        prefixText = newText
                        viewModel.updatePrefix(true, newText)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Prefix Text") }
                )
            }
            
            HorizontalDivider()
            
            Text("Suffix (Spoken after amount)")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = settings.customSuffixEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.updateSuffix(enabled, suffixText)
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text(if (settings.customSuffixEnabled) "Enabled" else "Disabled")
            }
            if (settings.customSuffixEnabled) {
                OutlinedTextField(
                    value = suffixText,
                    onValueChange = { newText ->
                        suffixText = newText
                        viewModel.updateSuffix(true, newText)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Suffix Text") }
                )
            }
            
            Spacer(Modifier.weight(1f))
            Text("Use {amount} to insert the amount, and {time} to insert current time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

