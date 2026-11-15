package com.example.presentation.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhotoCameraFront
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SecurityUpdateWarning
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.database.entity.TransactionEntity
import com.example.di.AppModule
import com.example.domain.model.AnnouncementStatus
import com.example.ui.components.AnimatedRupeeText
import com.example.ui.components.Floating3DObjectBox
import com.example.ui.components.LivePulseBeacon
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.ThreeDActionButton
import com.example.ui.components.ThreeDTactileCard
import com.example.ui.theme.AppSemanticColors
import com.example.ui.theme.BrandAmber
import com.example.ui.theme.BrandCoral
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.BrandEmeraldDark
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalIsDarkTheme
import com.example.util.AnnouncementHelper
import com.example.util.BluetoothHelper
import com.example.util.PermissionHelper
import com.example.util.update.AppUpdateInfo
import com.example.util.update.AppUpdateManager
import com.example.util.update.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(20.dp)
    ) {
        val heights = if (isPlaying) listOf(h1, h2, h3, h4, h5) else listOf(0.3f, 0.3f, 0.3f, 0.3f, 0.3f)
        heights.forEach { factor ->
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .fillMaxHeight(factor)
                    .clip(CircleShape)
                    .background(tintColor)
            )
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToKiosk: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onNavigateToAppLock: () -> Unit = {},
    onNavigateToIntruderLogs: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val intruderRepo = AppModule.intruderRepository
    val intruderCount by (intruderRepo?.getLogCount() ?: remember { kotlinx.coroutines.flow.emptyFlow<Int>() })
        .collectAsState(initial = 0)

    var hasNotificationPermission by remember { mutableStateOf(PermissionHelper.isNotificationListenerEnabled(context)) }
    var isBatteryOptimized by remember { mutableStateOf(!PermissionHelper.isIgnoringBatteryOptimizations(context)) }
    var isBtConnected by remember { mutableStateOf(BluetoothHelper.isBluetoothAudioConnected(context)) }
    var showQrDialog by remember { mutableStateOf(false) }
    var isReplayingVoice by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }

    // Live refresh permission, battery & BT audio state whenever returning to HomeScreen
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = PermissionHelper.isNotificationListenerEnabled(context)
                isBatteryOptimized = !PermissionHelper.isIgnoringBatteryOptimizations(context)
                isBtConnected = BluetoothHelper.isBluetoothAudioConnected(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val manager = AppModule.updateManager ?: AppUpdateManager.getInstance()
                val result = manager.checkForUpdate(context)
                if (result is UpdateStatus.UpdateAvailable) {
                    val dismissedVer = context.getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE)
                        .getString("dismissed_version", "") ?: ""
                    availableUpdate = if (result.updateInfo.latestVersion != dismissedVer) {
                        result.updateInfo
                    } else null
                } else {
                    availableUpdate = null
                }
            } catch (_: Exception) {
                // Ignore silent update check failures
            }
        }
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Sleek Top Merchant Header (Uber/Swiggy style)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.appSettings.shopName.ifBlank { "Dasmo SoundBox" },
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LivePulseBeacon(
                                isActive = uiState.appSettings.isEnabled && hasNotificationPermission,
                                activeColor = BrandEmerald,
                                size = 8.dp
                            )
                            Text(
                                text = when {
                                    !hasNotificationPermission -> "Setup required • Tap Fix Now"
                                    uiState.appSettings.isEnabled -> "Live UPI & Bank Shield Active"
                                    else -> "SoundBox Paused"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (!hasNotificationPermission) BrandCoral else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val isDark = LocalIsDarkTheme.current
                    StatusPillBadge(
                        text = if (isBtConnected) "BT Speaker" else "Phone Audio",
                        color = if (isBtConnected) AppSemanticColors.emeraldText else MaterialTheme.colorScheme.onSurfaceVariant,
                        containerColor = if (isBtConnected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        leadingIcon = {
                            Icon(
                                imageVector = if (isBtConnected) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                                contentDescription = null,
                                tint = if (isBtConnected) AppSemanticColors.emeraldText else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }

            // 2. Alert Banners (Intruder, Notification Access, Battery, Update)
            item {
                val isDark = LocalIsDarkTheme.current
                AnimatedVisibility(
                    visible = intruderCount > 0,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    ThreeDTactileCard(
                        onClick = onNavigateToIntruderLogs,
                        containerColor = if (isDark) Color(0xFF230F13) else Color(0xFFFEF2F2),
                        depthColor = Color(0xFFFCA5A5),
                        borderColor = if (isDark) BrandCoral.copy(alpha = 0.5f) else Color(0xFFF87171)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BrandCoral.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.SecurityUpdateWarning, contentDescription = null, tint = AppSemanticColors.coralText)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "$intruderCount Intruder Attempt${if (intruderCount == 1) "" else "s"} Detected",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFFFECACA) else Color(0xFF991B1B),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "Tap to inspect captured selfie & app logs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
                                )
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = AppSemanticColors.coralText)
                        }
                    }
                }
            }

            if (!hasNotificationPermission) {
                item {
                    ThreeDTactileCard(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        depthColor = Color(0xFFFCA5A5),
                        borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Notification Access Needed",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "Allow listener access so payments announce automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                )
                            }
                            Button(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    } catch (_: Exception) {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                        } catch (_: Exception) {}
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Fix Now", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            if (isBatteryOptimized) {
                item {
                    val isDark = LocalIsDarkTheme.current
                    ThreeDTactileCard(
                        containerColor = if (isDark) Color(0xFF221A0A) else Color(0xFFFFFBEB),
                        depthColor = Color(0xFFFCD34D),
                        borderColor = if (isDark) BrandAmber.copy(alpha = 0.45f) else Color(0xFFFBBF24)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(BrandAmber.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = AppSemanticColors.amberText)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Keep SoundBox Alive 24×7",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isDark) Color(0xFFFDE68A) else Color(0xFF78350F)
                                )
                                Text(
                                    "Turn off battery optimization so Android never sleeps.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isDark) Color(0xFFFCD34D).copy(alpha = 0.85f) else Color(0xFF92400E)
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    } catch (_: Exception) {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                        } catch (_: Exception) {}
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Protect", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (availableUpdate != null) {
                item {
                    ThreeDTactileCard(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        depthColor = BrandIndigo.copy(alpha = 0.35f),
                        borderColor = BrandIndigo.copy(alpha = 0.4f),
                        onClick = onNavigateToSettings
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Update Available • v${availableUpdate?.latestVersion}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "Tap to review release notes & install",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            FilledTonalButton(onClick = onNavigateToSettings, shape = RoundedCornerShape(12.dp)) {
                                Text("Update")
                            }
                            IconButton(onClick = {
                                val verToDismiss = availableUpdate?.latestVersion ?: ""
                                if (verToDismiss.isNotBlank()) {
                                    context.getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE)
                                        .edit()
                                        .putString("dismissed_version", verToDismiss)
                                        .apply()
                                }
                                availableUpdate = null
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                }
            }

            // 3. 3D SoundBox Hero Power Card (Uber/Swiggy Dark Obsidian + Floating 3D Hardware Art)
            item {
                Status3DHeroCard(
                    isActive = uiState.appSettings.isEnabled,
                    onToggle = { viewModel.toggleServiceState(it) }
                )
            }

            // 4. Today's Collection KPI Pair (Animated Numbers + Tactile 3D Cards)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ThreeDTactileCard(
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.surface,
                        depthColor = Color(0xFFA7F3D0),
                        borderColor = BrandEmerald.copy(alpha = 0.35f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BrandEmerald.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = AppSemanticColors.emeraldText, modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    "TODAY'S SALES",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            AnimatedRupeeText(
                                amount = uiState.todayEarnings,
                                style = MaterialTheme.typography.headlineMedium,
                                color = AppSemanticColors.emeraldText
                            )
                        }
                    }

                    ThreeDTactileCard(
                        modifier = Modifier.weight(1f),
                        containerColor = MaterialTheme.colorScheme.surface,
                        depthColor = Color(0xFFC7D2FE),
                        borderColor = AppSemanticColors.indigoText.copy(alpha = 0.3f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppSemanticColors.indigoText.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = AppSemanticColors.indigoText, modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    "PAYMENTS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "${uiState.todayCount}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // 5. Quick Merchant Tools Grid (3D Bevel Shortcut Cards)
            item {
                val isDark = LocalIsDarkTheme.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickTool3DTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Terminal,
                        title = "Kiosk Stand",
                        subtitle = "Counter Mode",
                        accentColor = AppSemanticColors.emeraldText,
                        containerColor = MaterialTheme.colorScheme.surface,
                        depthColor = Color(0xFFA7F3D0),
                        titleColor = MaterialTheme.colorScheme.onSurface,
                        subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onNavigateToKiosk
                    )
                    QuickTool3DTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.QrCode2,
                        title = "Shop QR",
                        subtitle = "Accept UPI",
                        accentColor = AppSemanticColors.indigoText,
                        containerColor = MaterialTheme.colorScheme.surface,
                        depthColor = Color(0xFFC7D2FE),
                        titleColor = MaterialTheme.colorScheme.onSurface,
                        subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { showQrDialog = true }
                    )
                    QuickTool3DTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        title = "Reports",
                        subtitle = "Charts & CSV",
                        accentColor = AppSemanticColors.orangeText,
                        containerColor = MaterialTheme.colorScheme.surface,
                        depthColor = Color(0xFFFED7AA),
                        titleColor = MaterialTheme.colorScheme.onSurface,
                        subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onNavigateToAnalytics
                    )
                }
            }

            // 6. Security Quick Strip (App Lock & Intruder Logs)
            item {
                val isDark = LocalIsDarkTheme.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThreeDTactileCard(
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToAppLock,
                        cornerRadius = 18.dp,
                        depthDp = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text("App Lock", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (uiState.appSettings.appLockEnabled) "${if (uiState.appSettings.appLockType == "PATTERN") "Pattern" else "PIN"} On" else "Off",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (uiState.appSettings.appLockEnabled) AppSemanticColors.emeraldText else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    ThreeDTactileCard(
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToIntruderLogs,
                        cornerRadius = 18.dp,
                        depthDp = 2.dp,
                        containerColor = when {
                            intruderCount > 0 && isDark -> Color(0xFF230F13)
                            intruderCount > 0 -> Color(0xFFFEF2F2)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (intruderCount > 0) BrandCoral.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PhotoCameraFront,
                                    contentDescription = null,
                                    tint = if (intruderCount > 0) AppSemanticColors.coralText else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text("Intruders", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (intruderCount > 0) "$intruderCount Selfies" else "All Clear",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (intruderCount > 0) AppSemanticColors.coralText else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 7. Last Payment Spotlight Card (Replay + Day Summary)
            item {
                LastPaymentCard(
                    tx = uiState.lastPayment,
                    isReplaying = isReplayingVoice,
                    onReplay = { tx ->
                        scope.launch {
                            isReplayingVoice = true
                            try {
                                AnnouncementHelper.replayTransaction(context, tx)
                            } finally {
                                isReplayingVoice = false
                            }
                        }
                    },
                    onDaySummary = {
                        scope.launch {
                            isReplayingVoice = true
                            try {
                                AnnouncementHelper.announceDaySummary(context, uiState.todayCount, uiState.todayEarnings)
                            } finally {
                                isReplayingVoice = false
                            }
                        }
                    }
                )
            }

            // 8. Quick Sound Test Studio Card
            item {
                ThreeDTactileCard(
                    containerColor = MaterialTheme.colorScheme.surface,
                    depthColor = MaterialTheme.colorScheme.outline
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text("Test SoundBox Voice", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Preview instant chime, customer name & voice clarity",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("test_phonepe_btn"),
                                shape = RoundedCornerShape(14.dp),
                                onClick = { viewModel.simulateTestPayment(100.0, null, "PhonePe", context) }
                            ) {
                                Text("₹100 PhonePe", fontWeight = FontWeight.SemiBold)
                            }
                            ThreeDActionButton(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("test_gpay_btn"),
                                height = 46.dp,
                                containerColor = BrandIndigo,
                                depthColor = Color(0xFF3730A3),
                                onClick = { viewModel.simulateTestPayment(500.0, "Rahul Sharma", "Google Pay", context) }
                            ) {
                                Text("₹500 GPay", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // 9. Live Activity Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent Payments",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    TextButton(onClick = onNavigateToHistory) {
                        Text("See All", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (uiState.liveActivity.isEmpty()) {
                item {
                    ThreeDTactileCard(
                        containerColor = MaterialTheme.colorScheme.surface,
                        depthColor = MaterialTheme.colorScheme.outline
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val isDark = LocalIsDarkTheme.current
                            Floating3DObjectBox(bobDistanceDp = 4f) {
                                Image(
                                    painter = painterResource(
                                        id = if (isDark) R.drawable.img_3d_empty_payments_dark else R.drawable.img_3d_empty_payments
                                    ),
                                    contentDescription = "Waiting for UPI payments",
                                    modifier = Modifier
                                        .size(124.dp)
                                        .clip(RoundedCornerShape(24.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Text(
                                "Ready for First Payment",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Incoming PhonePe, GPay, Paytm, BHIM & Bank alerts will appear and speak here automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(uiState.liveActivity, key = { it.id }) { tx ->
                    ActivityRow(
                        tx = tx,
                        onReplay = {
                            scope.launch {
                                AnnouncementHelper.replayTransaction(context, tx)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showQrDialog) {
        ShopQrDialog(
            initialUpiId = uiState.appSettings.shopUpiId,
            initialShopName = uiState.appSettings.shopName,
            onSaveUpiDetails = { upi, name -> viewModel.updateShopUpi(upi, name) },
            onDismiss = { showQrDialog = false }
        )
    }
}

@Composable
fun Status3DHeroCard(isActive: Boolean, onToggle: (Boolean) -> Unit) {
    val isDark = LocalIsDarkTheme.current

    val targetTop = when {
        isDark && isActive -> Color(0xFF0D1117)
        isDark && !isActive -> Color(0xFF2E0D12)
        !isDark && isActive -> Color.White
        else -> Color(0xFFFFF5F5)
    }
    val targetBottom = when {
        isDark && isActive -> Color(0xFF151C28)
        isDark && !isActive -> Color(0xFF47121A)
        !isDark && isActive -> Color(0xFFECFDF5)
        else -> Color(0xFFFEE2E2)
    }
    val targetDepth = when {
        isDark && isActive -> Color(0xFF020305)
        isDark && !isActive -> Color(0xFF1F0609)
        !isDark && isActive -> Color(0xFFA7F3D0)
        else -> Color(0xFFFECACA)
    }

    val bgTop by animateColorAsState(
        targetValue = targetTop,
        animationSpec = tween(350),
        label = "heroBgTop"
    )
    val bgBottom by animateColorAsState(
        targetValue = targetBottom,
        animationSpec = tween(350),
        label = "heroBgBottom"
    )
    val depthLip by animateColorAsState(
        targetValue = targetDepth,
        animationSpec = tween(350),
        label = "heroDepth"
    )

    val titleColor = when {
        isDark -> Color.White
        isActive -> Color(0xFF0F172A)
        else -> Color(0xFF7F1D1D)
    }
    val subtitleColor = when {
        isDark -> Color.White.copy(alpha = 0.78f)
        isActive -> Color(0xFF475569)
        else -> Color(0xFF991B1B)
    }
    val pillTextColor = when {
        isDark && isActive -> Color(0xFFA7F3D0)
        isDark && !isActive -> Color(0xFFFECACA)
        !isDark && isActive -> BrandEmeraldDark
        else -> Color(0xFFDC2626)
    }
    val pillContainerColor = when {
        isDark && isActive -> BrandEmerald.copy(alpha = 0.22f)
        isDark && !isActive -> Color.Black.copy(alpha = 0.25f)
        !isDark && isActive -> Color(0xFFD1FAE5)
        else -> Color(0xFFFECACA).copy(alpha = 0.65f)
    }

    ThreeDTactileCard(
        onClick = { onToggle(!isActive) },
        containerColor = bgTop,
        depthColor = depthLip,
        borderColor = if (isActive) BrandEmerald.copy(alpha = if (isDark) 0.48f else 0.40f)
        else BrandCoral.copy(alpha = if (isDark) 0.45f else 0.40f),
        cornerRadius = 26.dp,
        depthDp = 4.dp,
        elevationDp = if (isDark) 8.dp else 5.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(bgTop, bgBottom)))
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Floating3DObjectBox(bobDistanceDp = 3.5f, durationMs = 2400) {
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(
                                width = 1.5.dp,
                                color = if (isActive) {
                                    BrandEmerald.copy(alpha = if (isDark) 0.55f else 0.45f)
                                } else if (isDark) {
                                    Color.White.copy(alpha = 0.22f)
                                } else {
                                    BrandCoral.copy(alpha = 0.35f)
                                },
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
                        Image(
                            painter = painterResource(
                                id = if (isDark) R.drawable.img_3d_soundbox_hero_dark else R.drawable.img_3d_soundbox_hero
                            ),
                            contentDescription = "3D SoundBox Speaker",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusPillBadge(
                        text = if (isActive) "ONLINE • LISTENING" else "PAUSED",
                        color = pillTextColor,
                        containerColor = pillContainerColor
                    )
                    Text(
                        text = if (isActive) "SoundBox Active" else "SoundBox Muted",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = titleColor
                    )
                    Text(
                        text = if (isActive) "Speaking UPI & Bank credits loudly" else "Tap anywhere to resume alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor
                    )
                }

                Switch(
                    checked = isActive,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("soundbox_power_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BrandEmerald,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color(0xFFCBD5E1)
                    )
                )
            }
        }
    }
}

@Composable
private fun QuickTool3DTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    containerColor: Color,
    depthColor: Color,
    titleColor: Color,
    subtitleColor: Color,
    onClick: () -> Unit
) {
    ThreeDTactileCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = containerColor,
        depthColor = depthColor,
        cornerRadius = 20.dp,
        depthDp = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun LastPaymentCard(
    tx: TransactionEntity?,
    isReplaying: Boolean,
    onReplay: (TransactionEntity) -> Unit,
    onDaySummary: () -> Unit
) {
    ThreeDTactileCard(
        containerColor = MaterialTheme.colorScheme.surface,
        depthColor = Color(0xFFC7D2FE),
        borderColor = BrandIndigo.copy(alpha = 0.25f),
        cornerRadius = 24.dp,
        depthDp = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPillBadge(
                    text = "LATEST PAYMENT",
                    color = AppSemanticColors.indigoText,
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
                if (isReplaying) {
                    AudioEqualizerWave(isPlaying = true, tintColor = AppSemanticColors.indigoText)
                }
            }

            if (tx != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            tx.amountFormatted,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = AppSemanticColors.emeraldText
                        )
                        val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(tx.timestamp))
                        Text(
                            text = buildString {
                                if (!tx.payerName.isNullOrBlank()) {
                                    append("From ${tx.payerName} • ")
                                }
                                append("${tx.sourceAppName} • $time")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                tx.refId?.let { ref ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "UTR: $ref",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThreeDActionButton(
                        onClick = { onReplay(tx) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("replay_last_btn"),
                        height = 46.dp,
                        containerColor = BrandIndigo,
                        depthColor = Color(0xFF3730A3)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Replay (दोहराएं)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                    }

                    ThreeDActionButton(
                        onClick = onDaySummary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("day_summary_btn"),
                        height = 46.dp,
                        containerColor = BrandEmerald,
                        depthColor = BrandEmeraldDark
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("दिन का हिसाब", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                    }
                }
            } else {
                Text(
                    "No payments received yet today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ThreeDActionButton(
                    onClick = onDaySummary,
                    modifier = Modifier.fillMaxWidth(),
                    height = 46.dp,
                    containerColor = BrandIndigo,
                    depthColor = Color(0xFF3730A3)
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Speak Day Summary (दिन का हिसाब)", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun ActivityRow(
    tx: TransactionEntity,
    onReplay: () -> Unit = {}
) {
    val context = LocalContext.current
    val statusColor = when (tx.status) {
        AnnouncementStatus.ANNOUNCED.name -> BrandEmerald
        AnnouncementStatus.DUPLICATE_SKIPPED.name -> BrandAmber
        else -> BrandCoral
    }

    val statusText = when (tx.status) {
        AnnouncementStatus.ANNOUNCED.name -> "Announced"
        AnnouncementStatus.DUPLICATE_SKIPPED.name -> "Skipped"
        else -> "Error"
    }

    val appBadgeColor = when {
        tx.sourceAppName.contains("PhonePe", true) -> Color(0xFF7C3AED)
        tx.sourceAppName.contains("Google", true) || tx.sourceAppName.contains("GPay", true) -> Color(0xFF2563EB)
        tx.sourceAppName.contains("Paytm", true) -> Color(0xFF0284C7)
        tx.sourceAppName.contains("BHIM", true) -> Color(0xFFEA580C)
        else -> BrandEmeraldDark
    }

    val badgeInitial = tx.payerName?.firstOrNull()?.uppercaseChar()?.toString()
        ?: tx.sourceAppName.firstOrNull()?.uppercaseChar()?.toString()
        ?: "₹"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sleek Avatar Circle with Status Dot
            Box(modifier = Modifier.size(44.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(appBadgeColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeInitial,
                        fontWeight = FontWeight.ExtraBold,
                        color = appBadgeColor,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(1.5.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        tx.amountFormatted,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (tx.status == AnnouncementStatus.ANNOUNCED.name) AppSemanticColors.emeraldText else MaterialTheme.colorScheme.onSurface
                    )
                    tx.payerName?.let {
                        Text(
                            "• $it",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    tx.sourceAppName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                tx.refId?.let { ref ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("UTR", ref))
                                Toast.makeText(context, "Copied UTR: $ref", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "UTR: $ref",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMono,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy UTR",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(tx.timestamp))
                Text(time, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    IconButton(
                        onClick = onReplay,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Replay Announcement",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
