package com.example.presentation.screens.settings

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.BuildConfig
import com.example.util.update.AppUpdateInfo
import com.example.util.update.AppUpdateManager
import com.example.util.update.UpdateStatus
import android.content.Intent
import android.provider.Settings
import com.example.service.SoundBoxForegroundService
import com.example.util.PermissionHelper
import com.example.ui.theme.*
import com.example.util.AdminAuthManager
import com.example.util.ChimePlayer
import com.example.util.TonePreset
import com.example.util.TtsEngine

@Composable
fun AudioEqualizerWave(isPlaying: Boolean, tintColor: Color = MaterialTheme.colorScheme.primary) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = ""
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(480, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = ""
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(300, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = ""
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = ""
    )
    val h5 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(410, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = ""
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(26.dp)
    ) {
        val heights = if (isPlaying) listOf(h1, h2, h3, h4, h5) else listOf(0.3f, 0.3f, 0.3f, 0.3f, 0.3f)
        heights.forEach { factor ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(factor)
                    .clip(CircleShape)
                    .background(tintColor)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onNavigateToCustomMessage: () -> Unit
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val availableVoices by viewModel.availableVoices.collectAsStateWithLifecycle()
    val isPlayingPreview by viewModel.isPlayingPreview.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    
    var languageExpanded by remember { mutableStateOf(false) }
    var voiceExpanded by remember { mutableStateOf(false) }
    var testSampleText by remember { mutableStateOf("Paytm par 100 rupees received. Thank you!") }
    var shopNameText by remember(settings.shopName) { mutableStateOf(settings.shopName) }
    var shopUpiText by remember(settings.shopUpiId) { mutableStateOf(settings.shopUpiId) }
    var minAmountText by remember(settings.minAmountThreshold) { mutableStateOf(if (settings.minAmountThreshold > 0) settings.minAmountThreshold.toString() else "") }
    val context = LocalContext.current

    val languages = listOf(
        "en-IN" to "English (India)",
        "hi-IN" to "Hindi (हिन्दी)",
        "mr-IN" to "Marathi (मराठी)",
        "ta-IN" to "Tamil (தமிழ்)",
        "te-IN" to "Telugu (తెలుగు)",
        "gu-IN" to "Gujarati (ગુજરાતી)",
        "bn-IN" to "Bengali (বাংলা)",
        "kn-IN" to "Kannada (கன்னட)",
        "pa-IN" to "Punjabi (ਪੰਜਾਬੀ)",
        "ml-IN" to "Malayalam (മലയാളം)"
    )

    val currentLangLabel = languages.find { it.first == settings.language }?.second ?: "English (India)"
    val currentVoiceObj = availableVoices.find { it.name == settings.voiceName }

    Scaffold(
        topBar = { TopAppBar(title = { Text("⚙️ Settings & Voice Studio", fontWeight = FontWeight.Bold) }) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "🎨 Announcement & Voice Customization",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = languageExpanded,
                    onExpandedChange = { languageExpanded = !languageExpanded }
                ) {
                    OutlinedTextField(
                        value = currentLangLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Announcement Language") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = languageExpanded,
                        onDismissRequest = { languageExpanded = false }
                    ) {
                        languages.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label, fontWeight = FontWeight.Medium) },
                                onClick = {
                                    viewModel.updateLanguage(code)
                                    languageExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Voice Tone Presets Section with Vibrant Multi-Colors
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎙️ Cartoonish & Natural Voice Presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Tap card to apply", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    
                    val presetColors = listOf(
                        PrimaryContainer to OnPrimaryContainer,
                        SecondaryContainer to OnSecondaryContainer,
                        SoftCyanContainer to SkyCyan,
                        SoftYellowContainer to Color(0xFFD68910),
                        SoftPinkContainer to CandyPink
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(TtsEngine.TONE_PRESETS.size) { index ->
                            val preset = TtsEngine.TONE_PRESETS[index]
                            val isSelected = Math.abs(settings.voicePitch - preset.pitch) < 0.08f && Math.abs(settings.speechRate - preset.speed) < 0.08f
                            val (bg, fg) = presetColors[index % presetColors.size]

                            Card(
                                onClick = { viewModel.applyTonePreset(preset) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) bg else MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(20.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
                                modifier = Modifier.width(170.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val iconEmoji = when(preset.id) {
                                            "DEEP_WARM" -> "🤖"
                                            "NATURAL_SOFT" -> "🌸"
                                            "STANDARD_STUDIO" -> "🎙️"
                                            "BRIGHT_CLEAR" -> "⚡"
                                            else -> "🚀"
                                        }
                                        Text("$iconEmoji ${preset.title}", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium, color = if (isSelected) fg else MaterialTheme.colorScheme.onSurface)
                                        IconButton(
                                            onClick = { viewModel.previewVoiceSample(pitch = preset.pitch, rate = preset.speed) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play preview", tint = fg)
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(preset.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                    Spacer(Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = fg.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "Pitch: ${preset.pitch}x • Speed: ${preset.speed}x",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = fg
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Device Voice Option Dropdown with Preview Buttons
            if (availableVoices.isNotEmpty()) {
                item {
                    ExposedDropdownMenuBox(
                        expanded = voiceExpanded,
                        onExpandedChange = { voiceExpanded = !voiceExpanded }
                    ) {
                        OutlinedTextField(
                            value = currentVoiceObj?.displayName ?: if (settings.voiceName.isNotBlank()) settings.voiceName else "Default System Voice Engine",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Engine Voice Model") },
                            shape = RoundedCornerShape(16.dp),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
                        )
                        ExposedDropdownMenu(
                            expanded = voiceExpanded,
                            onDismissRequest = { voiceExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Default System Voice", fontWeight = FontWeight.Bold) },
                                onClick = {
                                    viewModel.updateVoiceName("")
                                    voiceExpanded = false
                                }
                            )
                            HorizontalDivider()
                            availableVoices.forEach { voice ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(voice.displayName, fontWeight = FontWeight.SemiBold)
                                                if (voice.isNetwork) {
                                                    Text("Online HD Voice ✨", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            IconButton(
                                                onClick = { viewModel.previewVoiceSample(voiceName = voice.name) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "Preview voice", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.updateVoiceName(voice.name)
                                        voiceExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Sliders for Pitch, Rate, and Volume in vibrant container
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("🎛️ Voice Sound Controls", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)

                        // Pitch Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Voice Pitch / Bass Tone", fontWeight = FontWeight.Bold)
                                }
                                val pitchLabel = when {
                                    settings.voicePitch < 0.85f -> "Deep Bass (${String.format("%.2f", settings.voicePitch)}x)"
                                    settings.voicePitch < 1.15f -> "Natural (${String.format("%.2f", settings.voicePitch)}x)"
                                    else -> "Crisp High (${String.format("%.2f", settings.voicePitch)}x)"
                                }
                                Text(pitchLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                            }
                            Slider(
                                value = settings.voicePitch,
                                onValueChange = { viewModel.updateVoicePitch(it) },
                                valueRange = 0.5f..1.8f,
                                steps = 26
                            )
                        }

                        // Speech Rate Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Speech Speed", fontWeight = FontWeight.Bold)
                                Text("${String.format("%.2f", settings.speechRate)}x", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.ExtraBold)
                            }
                            Slider(
                                value = settings.speechRate,
                                onValueChange = { viewModel.updateSpeechRate(it) },
                                valueRange = 0.5f..2.0f,
                                steps = 30
                            )
                        }

                        // Volume Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Speech Volume", fontWeight = FontWeight.Bold)
                                Text("${settings.announcementVolume}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.ExtraBold)
                            }
                            Slider(
                                value = settings.announcementVolume.toFloat(),
                                onValueChange = { viewModel.updateVolume(it.toInt()) },
                                valueRange = 0f..100f
                            )
                        }
                    }
                }
            }

            // Interactive Voice Equalizer Preview Tester Box
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("🔊 Interactive Voice Equalizer Tester", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            AudioEqualizerWave(isPlaying = isPlayingPreview, tintColor = MaterialTheme.colorScheme.primary)
                        }
                        
                        OutlinedTextField(
                            value = testSampleText,
                            onValueChange = { testSampleText = it },
                            label = { Text("Sample Announcement Phrase") },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.previewVoiceSample(customText = testSampleText) },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Play Preview", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { viewModel.stopAudio() },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Play Chime Before Announcement", fontWeight = FontWeight.Bold)
                                Text("Crisp tone before voice speaks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = settings.chimeEnabled, onCheckedChange = { viewModel.updateChime(it) })
                        }

                        if (settings.chimeEnabled) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Chime Tone Style", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(8.dp))
                                ChimePlayer.CHIME_OPTIONS.forEach { (code, title) ->
                                    val isSelected = settings.chimeSound.equals(code, ignoreCase = true)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { viewModel.updateChimeSound(code) }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            RadioButton(
                                                selected = isSelected,
                                                onClick = { viewModel.updateChimeSound(code) }
                                            )
                                            Text(title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
                                        }

                                        if (code != "MUTE") {
                                            IconButton(
                                                onClick = { viewModel.playChimePreview(context, code) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Play tone", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Announce Payer Name", fontWeight = FontWeight.Bold)
                                Text("Speak customer name from notification if available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = settings.announcePayerName, onCheckedChange = { viewModel.updateAnnouncePayerName(it) })
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bilingual Announcement (दोहरी घोषणा)", fontWeight = FontWeight.Bold)
                                Text("Speaks regional language first, then repeats key details in English", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = settings.bilingualEnabled, onCheckedChange = { viewModel.updateBilingualEnabled(it) })
                        }

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Flashlight Visual Strobe (एलईडी फ्लैश)", fontWeight = FontWeight.Bold)
                                Text("Flashes camera torch on incoming payments for noisy shops", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = settings.flashAlertEnabled, onCheckedChange = { viewModel.updateFlashAlertEnabled(it) })
                        }
                    }
                }
            }

            // Min Amount Threshold & Shop Profile
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("🏪 Merchant Countertop & Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        
                        OutlinedTextField(
                            value = shopNameText,
                            onValueChange = { shopNameText = it },
                            label = { Text("Shop / Business Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = shopUpiText,
                            onValueChange = { shopUpiText = it },
                            label = { Text("Shop UPI ID (for Counter QR & Reminders)") },
                            placeholder = { Text("merchant@okhdfcbank") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = minAmountText,
                            onValueChange = {
                                minAmountText = it
                                val amt = it.toDoubleOrNull() ?: 0.0
                                viewModel.updateMinAmountThreshold(amt)
                            },
                            label = { Text("Minimum Payment to Announce (₹)") },
                            placeholder = { Text("0 (Announce all amounts)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                viewModel.updateShopDetails(shopUpiText, shopNameText)
                                val amt = minAmountText.toDoubleOrNull() ?: 0.0
                                viewModel.updateMinAmountThreshold(amt)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save Merchant Profile")
                        }
                    }
                }
            }

            item { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)) }

            item {
                Text("📡 Auto Payment Detection Sources", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("UPI Notification Listener", fontWeight = FontWeight.Bold)
                                Text("PhonePe, GPay, Paytm, BHIM, etc.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = settings.notificationDetectionEnabled, onCheckedChange = { viewModel.updateNotificationDetection(it) })
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bank SMS Listener", fontWeight = FontWeight.Bold)
                                Text("Detect payments from official bank SMS alerts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = settings.smsDetectionEnabled, onCheckedChange = { viewModel.updateSmsDetection(it) })
                        }
                    }
                }
            }

            item { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)) }

            item {
                Text("🛡️ Background Keep-Alive & Reliability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            }

            item {
                val screenContext = LocalContext.current
                val isIgnoringBattery = remember { PermissionHelper.isIgnoringBatteryOptimizations(screenContext) }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Foreground SoundBox Service", fontWeight = FontWeight.Bold)
                                Text(
                                    if (SoundBoxForegroundService.isRunning) "Running (Sticky Foreground)" else "Standby (Auto-activates on payment)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (SoundBoxForegroundService.isRunning) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    SoundBoxForegroundService.start(screenContext)
                                }
                            ) {
                                Text("Restart")
                            }
                        }

                        HorizontalDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Battery Optimization Status", fontWeight = FontWeight.Bold)
                                Text(
                                    if (isIgnoringBattery) "Unrestricted (Safe from background kill)" else "Optimized (OS may kill in background)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isIgnoringBattery) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        screenContext.startActivity(intent)
                                    } catch (e: Exception) {
                                        val fallback = Intent(Settings.ACTION_SETTINGS)
                                        screenContext.startActivity(fallback)
                                    }
                                }
                            ) {
                                Text("Configure")
                            }
                        }
                    }
                }
            }

            item { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)) }

            item {
                Text("💬 Custom Voice Messages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToCustomMessage() },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Prefix Message", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(if (settings.customPrefixEnabled && settings.customPrefix.isNotBlank()) settings.customPrefix else "Disabled", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Text("Suffix Message", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(if (settings.customSuffixEnabled && settings.customSuffix.isNotBlank()) settings.customSuffix else "Disabled", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Text("Edit Custom Messages →", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            item { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)) }

            item {
                Text("🔒 Security & Admin Access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Admin Security", tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Authorized Admin Account", fontWeight = FontWeight.Bold)
                            Text(AdminAuthManager.ADMIN_EMAIL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("Firebase DB & System Settings strictly locked to Admin.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔄 Software Update", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            item {
                SoftwareUpdateCard(
                    updateState = updateState,
                    onCheckUpdates = { viewModel.checkForUpdates(context) },
                    onDownloadAndInstall = { info -> viewModel.downloadAndInstallUpdate(context, info) },
                    onInstallDownloaded = { file -> viewModel.installApk(context, file) },
                    onOpenBrowser = { url -> viewModel.openReleaseInBrowser(context, url) },
                    onDismiss = { viewModel.dismissUpdate() }
                )
            }
        }
    }
}

@Composable
fun SoftwareUpdateCard(
    updateState: UpdateStatus,
    onCheckUpdates: () -> Unit,
    onDownloadAndInstall: (AppUpdateInfo) -> Unit,
    onInstallDownloaded: (java.io.File) -> Unit,
    onOpenBrowser: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (updateState) {
                is UpdateStatus.UpdateAvailable -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Repo source tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "GitHub Release Channel",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${AppUpdateManager.GITHUB_OWNER}/${AppUpdateManager.GITHUB_REPO}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                OutlinedButton(
                    onClick = { onOpenBrowser(AppUpdateManager.REPO_RELEASES_WEB_URL) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Releases", style = MaterialTheme.typography.labelMedium)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            when (updateState) {
                is UpdateStatus.Idle -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Check if a newer version is published on GitHub.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = onCheckUpdates,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Check Update")
                        }
                    }
                }

                is UpdateStatus.Checking -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Column {
                            Text(
                                "Checking GitHub repository...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Fetching latest release manifest & APK tags",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is UpdateStatus.UpToDate -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF16A34A).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✓", color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text(
                                    "App is up to date!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A)
                                )
                                Text(
                                    "Running latest v${updateState.currentVersion}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = onCheckUpdates,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Re-check")
                        }
                    }
                }

                is UpdateStatus.UpdateAvailable -> {
                    val info = updateState.updateInfo
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Text(
                                    "✨ NEW VERSION: v${info.latestVersion}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                "Size: ${info.formattedSize}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            info.releaseTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Release Notes & Changes:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    info.changelog.take(350) + if (info.changelog.length > 350) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onDownloadAndInstall(info) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Update Now (APK)")
                            }
                            OutlinedButton(
                                onClick = { onOpenBrowser(info.htmlUrl) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("View")
                            }
                        }
                    }
                }

                is UpdateStatus.Downloading -> {
                    val info = updateState.updateInfo
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Downloading v${info.latestVersion}...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${updateState.progressPercent}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { updateState.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${updateState.bytesDownloaded / (1024 * 1024)} MB of ${if (updateState.totalBytes > 0) "${updateState.totalBytes / (1024 * 1024)} MB" else "unknown"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Keep app open",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                is UpdateStatus.ReadyToInstall -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF16A34A).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("📦", fontSize = 16.sp)
                            }
                            Column {
                                Text(
                                    "Download Complete!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "APK is ready for installation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Button(
                            onClick = { onInstallDownloaded(updateState.apkFile) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Install Update Now")
                        }
                    }
                }

                is UpdateStatus.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("⚠️", fontSize = 18.sp)
                            Text(
                                "Update Check Failed",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            updateState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onCheckUpdates,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Retry")
                            }
                            OutlinedButton(
                                onClick = { onOpenBrowser(AppUpdateManager.REPO_RELEASES_WEB_URL) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Open GitHub")
                            }
                        }
                    }
                }
            }
        }
    }
}



