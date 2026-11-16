package com.example.presentation.screens.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Pattern
import androidx.compose.material.icons.filled.PhotoCameraFront
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.domain.model.AppSettings
import com.example.service.SoundBoxForegroundService
import com.example.ui.components.LivePulseBeacon
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.ThreeDActionButton
import com.example.ui.components.ThreeDTactileCard
import com.example.ui.theme.AppSemanticColors
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.CandyPink
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.OnPrimaryContainer
import com.example.ui.theme.OnSecondaryContainer
import com.example.ui.theme.PrimaryContainer
import com.example.ui.theme.PrimaryContainerDark
import com.example.ui.theme.PrimaryDark
import com.example.ui.theme.SecondaryContainer
import com.example.ui.theme.SecondaryContainerDark
import com.example.ui.theme.SecondaryDark
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SoftCyanContainer
import com.example.ui.theme.SoftPinkContainer
import com.example.ui.theme.SoftYellowContainer
import com.example.util.AdminAuthManager
import com.example.util.ChimePlayer
import com.example.util.TtsEngine
import com.example.util.VoiceOption
import com.example.util.update.AppUpdateInfo
import com.example.util.update.AppUpdateManager
import com.example.util.update.UpdateStatus
import java.io.File

enum class SettingsCategory(
    val shortLabel: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    SHOP(
        shortLabel = "Shop",
        title = "Merchant & Shop Profile",
        subtitle = "Store name, UPI ID & alert threshold",
        icon = Icons.Default.Storefront
    ),
    VOICE(
        shortLabel = "Voice",
        title = "Voice, Chimes & Audio",
        subtitle = "Language, presets, equalizer & phrases",
        icon = Icons.AutoMirrored.Filled.VolumeUp
    ),
    DETECTION(
        shortLabel = "Detection",
        title = "Detection & Background Service",
        subtitle = "UPI apps, Bank SMS & battery reliability",
        icon = Icons.Default.NotificationsActive
    ),
    SECURITY(
        shortLabel = "Security",
        title = "Security & App Lock",
        subtitle = "PIN/Pattern lock, intruder selfies & admin",
        icon = Icons.Default.Shield
    ),
    SYSTEM(
        shortLabel = "Theme & App",
        title = "Appearance & Software Updates",
        subtitle = "White/Black theme & GitHub release channel",
        icon = Icons.Default.Palette
    )
}

@Composable
fun SettingsCategoryHeader(
    category: SettingsCategory,
    accentColor: Color,
    badgeText: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    showChevron: Boolean = true
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = showChevron) { onToggleExpand() }
            .testTag("settings_category_header_${category.name.lowercase()}"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = category.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            StatusPillBadge(
                text = badgeText,
                color = accentColor,
                containerColor = accentColor.copy(alpha = 0.12f)
            )

            if (showChevron) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse section" else "Expand section",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

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

@Composable
fun MerchantSettingsSection(
    shopNameText: String,
    onShopNameChange: (String) -> Unit,
    shopUpiText: String,
    onShopUpiChange: (String) -> Unit,
    minAmountText: String,
    onMinAmountChange: (String) -> Unit,
    onSave: () -> Unit
) {
    ThreeDTactileCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        depthColor = MaterialTheme.colorScheme.outline,
        cornerRadius = 22.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = shopNameText,
                onValueChange = onShopNameChange,
                label = { Text("Shop / Business Name") },
                supportingText = { Text("Shown on Counter QR & PDF Statements") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shop_name_input"),
                singleLine = true
            )

            OutlinedTextField(
                value = shopUpiText,
                onValueChange = onShopUpiChange,
                label = { Text("Shop UPI ID (for Counter QR & Reminders)") },
                placeholder = { Text("merchant@okhdfcbank") },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shop_upi_input"),
                singleLine = true
            )

            OutlinedTextField(
                value = minAmountText,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() || it == '.' }
                    if (filtered.count { it == '.' } <= 1) {
                        onMinAmountChange(filtered)
                    }
                },
                label = { Text("Minimum Payment to Announce (₹)") },
                placeholder = { Text("0 (Announce all amounts)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("min_amount_threshold_input"),
                singleLine = true
            )

            ThreeDActionButton(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_merchant_profile_btn"),
                height = 48.dp,
                containerColor = BrandIndigo,
                depthColor = Color(0xFF3730A3)
            ) {
                Text("Save Merchant Profile", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceStudioSection(
    context: Context,
    settings: AppSettings,
    languages: List<Pair<String, String>>,
    availableVoices: List<VoiceOption>,
    isPlayingPreview: Boolean,
    testSampleText: String,
    onTestSampleTextChange: (String) -> Unit,
    viewModel: SettingsViewModel,
    onNavigateToCustomMessage: () -> Unit
) {
    var languageExpanded by remember { mutableStateOf(false) }
    var voiceExpanded by remember { mutableStateOf(false) }

    val currentLangLabel = languages.find { it.first == settings.language }?.second ?: "English (India)"
    val currentVoiceObj = availableVoices.find { it.name == settings.voiceName }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. Language & Voice Engine Card (No loose fields floating outside cards)
        ThreeDTactileCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            depthColor = MaterialTheme.colorScheme.outline,
            cornerRadius = 22.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Language & Voice Engine",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )

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
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
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

                if (availableVoices.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = voiceExpanded,
                        onExpandedChange = { voiceExpanded = !voiceExpanded }
                    ) {
                        OutlinedTextField(
                            value = currentVoiceObj?.displayName
                                ?: if (settings.voiceName.isNotBlank()) settings.voiceName else "Default System Voice Engine",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Engine Voice Model") },
                            shape = RoundedCornerShape(14.dp),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.RecordVoiceOver,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
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
                                                    Text(
                                                        "Online HD Voice",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { viewModel.previewVoiceSample(voiceName = voice.name) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = "Preview voice",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
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
        }

        // 2. Voice Presets + Sound Controls Card
        ThreeDTactileCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            depthColor = MaterialTheme.colorScheme.outline,
            cornerRadius = 22.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Voice Tone Presets & Controls",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Tap card to apply",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                val isDark = LocalIsDarkTheme.current
                val presetColors = if (isDark) {
                    listOf(
                        PrimaryContainerDark to PrimaryDark,
                        SecondaryContainerDark to SecondaryDark,
                        Color(0xFF0C2438) to Color(0xFF38BDF8),
                        Color(0xFF2B200B) to Color(0xFFFBBF24),
                        Color(0xFF311124) to Color(0xFFF472B6)
                    )
                } else {
                    listOf(
                        PrimaryContainer to OnPrimaryContainer,
                        SecondaryContainer to OnSecondaryContainer,
                        SoftCyanContainer to SkyCyan,
                        SoftYellowContainer to Color(0xFFD68910),
                        SoftPinkContainer to CandyPink
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(TtsEngine.TONE_PRESETS.size) { index ->
                        val preset = TtsEngine.TONE_PRESETS[index]
                        val isSelected = Math.abs(settings.voicePitch - preset.pitch) < 0.08f &&
                            Math.abs(settings.speechRate - preset.speed) < 0.08f
                        val (bg, fg) = presetColors[index % presetColors.size]

                        ThreeDTactileCard(
                            onClick = { viewModel.applyTonePreset(preset) },
                            containerColor = if (isSelected) bg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            depthColor = if (isSelected) fg.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline,
                            borderColor = if (isSelected) fg.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline,
                            cornerRadius = 18.dp,
                            depthDp = if (isSelected) 3.dp else 2.dp,
                            modifier = Modifier.width(166.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        preset.title,
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isSelected) fg else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.previewVoiceSample(pitch = preset.pitch, rate = preset.speed) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play preview", tint = fg)
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    preset.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = fg.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = "Pitch ${preset.pitch}x • Speed ${preset.speed}x",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = fg
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))

                // Pitch Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.GraphicEq,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Voice Pitch", fontWeight = FontWeight.Bold)
                        }
                        val pitchLabel = when {
                            settings.voicePitch < 0.85f -> "Deep (${String.format("%.2f", settings.voicePitch)}x)"
                            settings.voicePitch < 1.15f -> "Natural (${String.format("%.2f", settings.voicePitch)}x)"
                            else -> "High (${String.format("%.2f", settings.voicePitch)}x)"
                        }
                        Text(
                            pitchLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold
                        )
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
                        Text(
                            "${String.format("%.2f", settings.speechRate)}x",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.ExtraBold
                        )
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
                        Text(
                            "${settings.announcementVolume}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Slider(
                        value = settings.announcementVolume.toFloat(),
                        onValueChange = { viewModel.updateVolume(it.toInt()) },
                        valueRange = 0f..100f
                    )
                }
            }
        }

        // 3. Interactive Voice Equalizer Tester Card
        ThreeDTactileCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            depthColor = BrandIndigo.copy(alpha = 0.3f),
            borderColor = BrandIndigo.copy(alpha = 0.25f),
            cornerRadius = 22.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Voice Equalizer Tester",
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    AudioEqualizerWave(isPlaying = isPlayingPreview, tintColor = MaterialTheme.colorScheme.primary)
                }

                OutlinedTextField(
                    value = testSampleText,
                    onValueChange = onTestSampleTextChange,
                    label = { Text("Sample Announcement Phrase") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ThreeDActionButton(
                        onClick = { viewModel.previewVoiceSample(customText = testSampleText) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_play_preview_btn"),
                        height = 46.dp,
                        containerColor = BrandIndigo,
                        depthColor = Color(0xFF3730A3)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Play Preview", fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    OutlinedButton(
                        onClick = { viewModel.stopAudio() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // 4. Chime & Alert Behavior Card
        ThreeDTactileCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            depthColor = MaterialTheme.colorScheme.outline,
            cornerRadius = 22.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Play Chime Before Announcement", fontWeight = FontWeight.Bold)
                        Text(
                            "Crisp tone before voice speaks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = settings.chimeEnabled, onCheckedChange = { viewModel.updateChime(it) })
                }

                AnimatedVisibility(visible = settings.chimeEnabled) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Chime Tone Style", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        ChimePlayer.CHIME_OPTIONS.forEach { (code, title) ->
                            val isSelected = settings.chimeSound.equals(code, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { viewModel.updateChimeSound(code) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
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
                                    Text(
                                        title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                if (code != "MUTE") {
                                    IconButton(
                                        onClick = { viewModel.playChimePreview(context, code) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = "Play tone",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Announce Payer Name", fontWeight = FontWeight.Bold)
                        Text(
                            "Speak customer name from notification if available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = settings.announcePayerName, onCheckedChange = { viewModel.updateAnnouncePayerName(it) })
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bilingual Announcement (दोहरी घोषणा)", fontWeight = FontWeight.Bold)
                        Text(
                            "Speaks regional language first, then repeats key details in English",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = settings.bilingualEnabled, onCheckedChange = { viewModel.updateBilingualEnabled(it) })
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Flashlight Visual Strobe (एलईडी फ्लैश)", fontWeight = FontWeight.Bold)
                        Text(
                            "Flashes camera torch on incoming payments for noisy shops",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = settings.flashAlertEnabled, onCheckedChange = { viewModel.updateFlashAlertEnabled(it) })
                }
            }
        }

        // 5. Custom Voice Phrases Shortcut Card
        ThreeDTactileCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNavigateToCustomMessage,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            depthColor = AppSemanticColors.emeraldText.copy(alpha = 0.35f),
            borderColor = AppSemanticColors.emeraldText.copy(alpha = 0.25f),
            cornerRadius = 22.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Custom Voice Prefix & Suffix",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "Prefix: ${if (settings.customPrefixEnabled && settings.customPrefix.isNotBlank()) settings.customPrefix else "Disabled"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                    Text(
                        "Suffix: ${if (settings.customSuffixEnabled && settings.customSuffix.isNotBlank()) settings.customSuffix else "Disabled"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Edit custom messages",
                    tint = AppSemanticColors.emeraldText
                )
            }
        }
    }
}

@Composable
fun DetectionReliabilitySection(
    context: Context,
    settings: AppSettings,
    isServiceRunning: Boolean,
    isIgnoringBattery: Boolean,
    onServiceStarted: () -> Unit,
    viewModel: SettingsViewModel
) {
    ThreeDTactileCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        depthColor = MaterialTheme.colorScheme.outline,
        cornerRadius = 22.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Payment Channels",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("UPI Notification Listener", fontWeight = FontWeight.Bold)
                    Text(
                        "PhonePe, GPay, Paytm, BHIM, etc.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.notificationDetectionEnabled,
                    onCheckedChange = { viewModel.updateNotificationDetection(it) }
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bank SMS Listener", fontWeight = FontWeight.Bold)
                    Text(
                        "Detect payments from official bank SMS alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.smsDetectionEnabled,
                    onCheckedChange = { viewModel.updateSmsDetection(it) }
                )
            }

            HorizontalDivider()

            Text(
                "Background Keep-Alive & Reliability",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LivePulseBeacon(
                        isActive = isServiceRunning,
                        activeColor = AppSemanticColors.emeraldText,
                        size = 8.dp
                    )
                    Column {
                        Text("Foreground SoundBox Service", fontWeight = FontWeight.Bold)
                        Text(
                            if (isServiceRunning) "Running (Sticky Foreground)" else "Standby (Auto-activates on payment)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isServiceRunning) AppSemanticColors.emeraldText else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                FilledTonalButton(
                    onClick = {
                        SoundBoxForegroundService.start(context)
                        onServiceStarted()
                        Toast.makeText(context, "SoundBox service refreshed", Toast.LENGTH_SHORT).show()
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
                        color = if (isIgnoringBattery) AppSemanticColors.emeraldText else MaterialTheme.colorScheme.error
                    )
                }
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    }
                ) {
                    Text("Configure")
                }
            }
        }
    }
}

@Composable
fun SecurityPrivacySection(
    settings: AppSettings,
    intruderCount: Int,
    onNavigateToAppLock: () -> Unit,
    onNavigateToIntruderLogs: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ThreeDTactileCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNavigateToAppLock,
            containerColor = MaterialTheme.colorScheme.surface,
            depthColor = MaterialTheme.colorScheme.outline,
            cornerRadius = 22.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (settings.appLockType) {
                                "PATTERN" -> Icons.Default.Pattern
                                "PASSWORD" -> Icons.Default.Password
                                else -> Icons.Default.Lock
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text("App Lock Protection", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        val lockMethodName = when (settings.appLockType) {
                            "PATTERN" -> "Pattern Lock"
                            "PASSWORD" -> "Password Lock"
                            else -> "PIN Lock"
                        }
                        Text(
                            text = if (settings.appLockEnabled) {
                                "$lockMethodName Active (${settings.lockedAppPackages.size} apps)"
                            } else "Disabled • Tap to configure",
                            fontSize = 12.sp,
                            color = if (settings.appLockEnabled) AppSemanticColors.emeraldText else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (settings.appLockEnabled) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ThreeDTactileCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNavigateToIntruderLogs,
            containerColor = if (intruderCount > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface,
            depthColor = if (intruderCount > 0) AppSemanticColors.coralText.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outline,
            borderColor = if (intruderCount > 0) AppSemanticColors.coralText.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outline,
            cornerRadius = 22.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (intruderCount > 0) AppSemanticColors.coralText.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.primaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCameraFront,
                            contentDescription = null,
                            tint = if (intruderCount > 0) AppSemanticColors.coralText else MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text("Intruder Photos & Logs", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (intruderCount > 0) {
                                "$intruderCount unauthorized attempt${if (intruderCount == 1) "" else "s"} logged!"
                            } else "No intruder attempts recorded",
                            fontSize = 12.sp,
                            color = if (intruderCount > 0) AppSemanticColors.coralText else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (intruderCount > 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (intruderCount > 0) AppSemanticColors.coralText else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ThreeDTactileCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            depthColor = MaterialTheme.colorScheme.outline,
            cornerRadius = 22.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Admin Security", tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Authorized Admin Account", fontWeight = FontWeight.Bold)
                    Text(
                        AdminAuthManager.ADMIN_EMAIL,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Cloud & security rules locked to authorized admin.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AppearanceAndUpdateSection(
    context: Context,
    settings: AppSettings,
    updateState: UpdateStatus,
    viewModel: SettingsViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. App Theme Card
        ThreeDTactileCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            depthColor = MaterialTheme.colorScheme.outline,
            cornerRadius = 22.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "App Theme",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "White default on install • Switch to Black or System anytime",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "LIGHT" to "White",
                        "DARK" to "Black",
                        "SYSTEM" to "System"
                    ).forEach { (key, label) ->
                        val selected = settings.theme == key
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.updateTheme(key) }
                                .testTag("theme_option_${key.lowercase()}"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Software Update Card
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

@Composable
fun SoftwareUpdateCard(
    updateState: UpdateStatus,
    onCheckUpdates: () -> Unit,
    onDownloadAndInstall: (AppUpdateInfo) -> Unit,
    onInstallDownloaded: (File) -> Unit,
    onOpenBrowser: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") onDismiss: () -> Unit
) {
    ThreeDTactileCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = when (updateState) {
            is UpdateStatus.UpdateAvailable -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.surface
        },
        depthColor = MaterialTheme.colorScheme.outline,
        cornerRadius = 22.dp
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
                    Text(
                        "Software Update",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "${AppUpdateManager.GITHUB_OWNER}/${AppUpdateManager.GITHUB_REPO}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = { onOpenBrowser(AppUpdateManager.REPO_RELEASES_WEB_URL) },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Releases", style = MaterialTheme.typography.labelSmall)
                    }
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
                            "Build ${BuildConfig.VERSION_CODE} • Check GitHub for newer releases.",
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
                            modifier = Modifier.size(22.dp),
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
                                    .background(AppSemanticColors.emeraldText.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✓", color = AppSemanticColors.emeraldText, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text(
                                    "App is up to date!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = AppSemanticColors.emeraldText
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
                                    "NEW VERSION: v${info.latestVersion}",
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
                                    "Release Notes:",
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
                                "${updateState.bytesDownloaded / (1024 * 1024)} MB of ${
                                    if (updateState.totalBytes > 0) "${updateState.totalBytes / (1024 * 1024)} MB" else "unknown"
                                }",
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
                        Column {
                            Text(
                                "Download Complete!",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = AppSemanticColors.emeraldText
                            )
                            Text(
                                "APK is ready for installation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                        Text(
                            "Update Check Failed",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
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
