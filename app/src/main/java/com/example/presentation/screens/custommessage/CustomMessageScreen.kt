package com.example.presentation.screens.custommessage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.ThreeDTactileCard
import kotlinx.coroutines.delay

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

    LaunchedEffect(prefixText) {
        if (isPrefixLoaded && prefixText != settings.customPrefix) {
            delay(400L)
            viewModel.updatePrefix(settings.customPrefixEnabled, prefixText)
        }
    }

    LaunchedEffect(suffixText) {
        if (isSuffixLoaded && suffixText != settings.customSuffix) {
            delay(400L)
            viewModel.updateSuffix(settings.customSuffixEnabled, suffixText)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Custom Voice Messages", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ThreeDTactileCard(
                containerColor = MaterialTheme.colorScheme.surface,
                depthColor = MaterialTheme.colorScheme.outline
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Prefix Message", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("Spoken right before the amount", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = settings.customPrefixEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.updatePrefix(enabled, prefixText)
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (settings.customPrefixEnabled) "On" else "Off",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (settings.customPrefixEnabled) {
                        OutlinedTextField(
                            value = prefixText,
                            onValueChange = { prefixText = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            label = { Text("Prefix Text") }
                        )
                    }
                }
            }

            ThreeDTactileCard(
                containerColor = MaterialTheme.colorScheme.surface,
                depthColor = MaterialTheme.colorScheme.outline
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Suffix Message", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("Spoken right after the amount", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = settings.customSuffixEnabled,
                                onCheckedChange = { enabled ->
                                    viewModel.updateSuffix(enabled, suffixText)
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (settings.customSuffixEnabled) "On" else "Off",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (settings.customSuffixEnabled) {
                        OutlinedTextField(
                            value = suffixText,
                            onValueChange = { suffixText = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            label = { Text("Suffix Text") }
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                text = "Tip: Use {amount} to insert the rupee amount and {time} to insert the current time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
