package com.example.presentation.screens.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.di.AppModule
import com.example.service.SoundBoxForegroundService
import com.example.ui.theme.AppSemanticColors
import com.example.util.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onNavigateToCustomMessage: () -> Unit,
    onNavigateToAppLock: () -> Unit = {},
    onNavigateToIntruderLogs: () -> Unit = {}
) {
    val settings by viewModel.uiState.collectAsStateWithLifecycle()
    val isSettingsLoaded by viewModel.isSettingsLoaded.collectAsStateWithLifecycle()
    val availableVoices by viewModel.availableVoices.collectAsStateWithLifecycle()
    val isPlayingPreview by viewModel.isPlayingPreview.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val intruderRepo = AppModule.intruderRepository
    val intruderCount by (intruderRepo?.getLogCount() ?: remember { emptyFlow() })
        .collectAsState(initial = 0)

    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }
    val expandedState = remember {
        mutableStateMapOf(
            SettingsCategory.SHOP to true,
            SettingsCategory.VOICE to true,
            SettingsCategory.DETECTION to true,
            SettingsCategory.SECURITY to true,
            SettingsCategory.SYSTEM to true
        )
    }

    var testSampleText by remember { mutableStateOf("Paytm par 100 rupees received. Thank you!") }
    var shopNameText by remember { mutableStateOf("") }
    var shopUpiText by remember { mutableStateOf("") }
    var minAmountText by remember { mutableStateOf("") }
    var isProfileLoaded by remember { mutableStateOf(false) }
    var lastSyncedShopName by remember { mutableStateOf<String?>(null) }
    var lastSyncedShopUpi by remember { mutableStateOf<String?>(null) }
    var lastSyncedMinAmount by remember { mutableStateOf<Double?>(null) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isIgnoringBattery by remember { mutableStateOf(PermissionHelper.isIgnoringBatteryOptimizations(context)) }
    var isServiceRunning by remember { mutableStateOf(SoundBoxForegroundService.isRunning) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isIgnoringBattery = PermissionHelper.isIgnoringBatteryOptimizations(context)
                isServiceRunning = SoundBoxForegroundService.isRunning
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isSettingsLoaded, settings.shopName, settings.shopUpiId, settings.minAmountThreshold) {
        if (!isSettingsLoaded) return@LaunchedEffect
        val formattedMin = if (settings.minAmountThreshold > 0) {
            if (settings.minAmountThreshold % 1.0 == 0.0) settings.minAmountThreshold.toInt().toString()
            else settings.minAmountThreshold.toString()
        } else ""

        if (!isProfileLoaded) {
            shopNameText = settings.shopName
            shopUpiText = settings.shopUpiId
            minAmountText = formattedMin
            lastSyncedShopName = settings.shopName
            lastSyncedShopUpi = settings.shopUpiId
            lastSyncedMinAmount = settings.minAmountThreshold
            isProfileLoaded = true
        } else {
            if (shopNameText.trim() == lastSyncedShopName) {
                shopNameText = settings.shopName
            }
            lastSyncedShopName = settings.shopName

            if (shopUpiText.trim() == lastSyncedShopUpi) {
                shopUpiText = settings.shopUpiId
            }
            lastSyncedShopUpi = settings.shopUpiId

            val localMin = minAmountText.toDoubleOrNull() ?: 0.0
            if (localMin == lastSyncedMinAmount) {
                minAmountText = formattedMin
            }
            lastSyncedMinAmount = settings.minAmountThreshold
        }
    }

    // Debounced auto-save for Shop Name & UPI ID so edits never vanish when leaving Settings
    LaunchedEffect(isProfileLoaded, shopNameText, shopUpiText) {
        if (isProfileLoaded) {
            delay(350L)
            val trimmedName = shopNameText.trim()
            val trimmedUpi = shopUpiText.trim()
            val hasNameChange = trimmedName.isNotEmpty() && trimmedName != settings.shopName
            val hasUpiChange = trimmedUpi != settings.shopUpiId
            if (hasNameChange || hasUpiChange) {
                if (hasNameChange) lastSyncedShopName = trimmedName
                if (hasUpiChange) lastSyncedShopUpi = trimmedUpi
                viewModel.flushMerchantProfile(
                    upiId = trimmedUpi,
                    shopName = trimmedName,
                    minAmountThreshold = minAmountText.toDoubleOrNull() ?: settings.minAmountThreshold
                )
            }
        }
    }

    // Debounced persistence for minAmountText without clobbering cursor/decimals
    LaunchedEffect(isProfileLoaded, minAmountText) {
        if (isProfileLoaded) {
            delay(450L)
            val parsed = minAmountText.toDoubleOrNull() ?: 0.0
            if (parsed != settings.minAmountThreshold) {
                lastSyncedMinAmount = parsed
                viewModel.updateMinAmountThreshold(parsed)
            }
        }
    }

    // Flush any unsaved typing immediately if user navigates back before debounce fires
    val latestShopName by rememberUpdatedState(shopNameText)
    val latestShopUpi by rememberUpdatedState(shopUpiText)
    val latestMinAmount by rememberUpdatedState(minAmountText)
    val latestLoaded by rememberUpdatedState(isProfileLoaded)
    val latestSettings by rememberUpdatedState(settings)

    DisposableEffect(Unit) {
        onDispose {
            if (latestLoaded) {
                val trimmedName = latestShopName.trim()
                val trimmedUpi = latestShopUpi.trim()
                val parsedMin = latestMinAmount.toDoubleOrNull() ?: 0.0
                if ((trimmedName.isNotEmpty() && trimmedName != latestSettings.shopName) ||
                    trimmedUpi != latestSettings.shopUpiId ||
                    parsedMin != latestSettings.minAmountThreshold
                ) {
                    viewModel.flushMerchantProfile(
                        upiId = trimmedUpi,
                        shopName = trimmedName,
                        minAmountThreshold = parsedMin
                    )
                }
            }
        }
    }

    val languages = remember {
        listOf(
            "en-IN" to "English (India)",
            "hi-IN" to "Hindi (हिन्दी)",
            "mr-IN" to "Marathi (मराठी)",
            "ta-IN" to "Tamil (தமிழ்)",
            "te-IN" to "Telugu (తెలుగు)",
            "gu-IN" to "Gujarati (ગુજરાતી)",
            "bn-IN" to "Bengali (বাংলা)",
            "kn-IN" to "Kannada (ಕನ್ನಡ)",
            "pa-IN" to "Punjabi (ਪੰਜਾਬੀ)",
            "ml-IN" to "Malayalam (മലയാളം)"
        )
    }

    val visibleCategories = remember(selectedCategory) {
        selectedCategory?.let { listOf(it) } ?: SettingsCategory.entries
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Title Header
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Settings & Voice Studio",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Organized by category for quick store & audio setup",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Category Filter Chip Strip (All + 5 Categories)
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    item {
                        val isAllSelected = selectedCategory == null
                        CategoryFilterPill(
                            label = "All Categories",
                            icon = Icons.Default.Tune,
                            selected = isAllSelected,
                            onClick = { selectedCategory = null },
                            testTag = "settings_cat_all"
                        )
                    }
                    items(SettingsCategory.entries) { category ->
                        CategoryFilterPill(
                            label = category.shortLabel,
                            icon = category.icon,
                            selected = selectedCategory == category,
                            onClick = {
                                selectedCategory = category
                                expandedState[category] = true
                            },
                            testTag = "settings_cat_${category.name.lowercase()}"
                        )
                    }
                }
            }

            // Organized Category Sections
            items(visibleCategories, key = { it.name }) { category ->
                val showChevron = selectedCategory == null
                val isExpanded = if (selectedCategory != null) true else (expandedState[category] ?: true)

                val (accentColor, badgeText) = when (category) {
                    SettingsCategory.SHOP -> AppSemanticColors.indigoText to
                        settings.shopName.ifBlank { "My Shop" }.take(14)
                    SettingsCategory.VOICE -> AppSemanticColors.emeraldText to
                        settings.language.uppercase()
                    SettingsCategory.DETECTION -> AppSemanticColors.amberText to
                        if (settings.notificationDetectionEnabled || settings.smsDetectionEnabled) "ACTIVE" else "PAUSED"
                    SettingsCategory.SECURITY -> (
                        if (intruderCount > 0) AppSemanticColors.coralText else AppSemanticColors.indigoText
                    ) to (
                        if (settings.appLockEnabled) "LOCK ON" else "CONFIG"
                    )
                    SettingsCategory.SYSTEM -> AppSemanticColors.orangeText to
                        when (settings.theme) {
                            "DARK" -> "BLACK"
                            "SYSTEM" -> "SYSTEM"
                            else -> "WHITE"
                        }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsCategoryHeader(
                        category = category,
                        accentColor = accentColor,
                        badgeText = badgeText,
                        isExpanded = isExpanded,
                        showChevron = showChevron,
                        onToggleExpand = { expandedState[category] = !isExpanded }
                    )

                    AnimatedVisibility(visible = isExpanded) {
                        when (category) {
                            SettingsCategory.SHOP -> {
                                MerchantSettingsSection(
                                    shopNameText = shopNameText,
                                    onShopNameChange = { shopNameText = it },
                                    shopUpiText = shopUpiText,
                                    onShopUpiChange = { shopUpiText = it },
                                    minAmountText = minAmountText,
                                    onMinAmountChange = { minAmountText = it },
                                    onSave = {
                                        val cleanShop = shopNameText.trim().ifBlank { "My Shop" }
                                        val cleanUpi = shopUpiText.trim()
                                        val amt = minAmountText.toDoubleOrNull() ?: 0.0
                                        shopNameText = cleanShop
                                        shopUpiText = cleanUpi
                                        lastSyncedShopName = cleanShop
                                        lastSyncedShopUpi = cleanUpi
                                        lastSyncedMinAmount = amt
                                        viewModel.updateShopDetails(cleanUpi, cleanShop)
                                        viewModel.updateMinAmountThreshold(amt)
                                        Toast.makeText(context, "Merchant profile saved", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            SettingsCategory.VOICE -> {
                                VoiceStudioSection(
                                    context = context,
                                    settings = settings,
                                    languages = languages,
                                    availableVoices = availableVoices,
                                    isPlayingPreview = isPlayingPreview,
                                    testSampleText = testSampleText,
                                    onTestSampleTextChange = { testSampleText = it },
                                    viewModel = viewModel,
                                    onNavigateToCustomMessage = onNavigateToCustomMessage
                                )
                            }

                            SettingsCategory.DETECTION -> {
                                DetectionReliabilitySection(
                                    context = context,
                                    settings = settings,
                                    isServiceRunning = isServiceRunning,
                                    isIgnoringBattery = isIgnoringBattery,
                                    onServiceStarted = { isServiceRunning = true },
                                    viewModel = viewModel
                                )
                            }

                            SettingsCategory.SECURITY -> {
                                SecurityPrivacySection(
                                    settings = settings,
                                    intruderCount = intruderCount,
                                    onNavigateToAppLock = onNavigateToAppLock,
                                    onNavigateToIntruderLogs = onNavigateToIntruderLogs
                                )
                            }

                            SettingsCategory.SYSTEM -> {
                                AppearanceAndUpdateSection(
                                    context = context,
                                    settings = settings,
                                    updateState = updateState,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
