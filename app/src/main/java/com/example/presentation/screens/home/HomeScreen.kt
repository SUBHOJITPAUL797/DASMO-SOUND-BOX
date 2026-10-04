package com.example.presentation.screens.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.entity.TransactionEntity
import com.example.domain.model.AnnouncementStatus
import com.example.di.AppModule
import com.example.util.AnnouncementHelper
import com.example.util.BluetoothHelper
import com.example.util.PermissionHelper
import com.example.util.update.AppUpdateInfo
import com.example.util.update.AppUpdateManager
import com.example.util.update.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*


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
    onNavigateToAnalytics: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasNotificationPermission by remember { mutableStateOf(PermissionHelper.isNotificationListenerEnabled(context)) }
    var isBatteryOptimized by remember { mutableStateOf(!PermissionHelper.isIgnoringBatteryOptimizations(context)) }
    var isBtConnected by remember { mutableStateOf(BluetoothHelper.isBluetoothAudioConnected(context)) }
    var showQrDialog by remember { mutableStateOf(false) }
    var isReplayingVoice by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<AppUpdateInfo?>(null) }

    LaunchedEffect(Unit) {
        hasNotificationPermission = PermissionHelper.isNotificationListenerEnabled(context)
        isBatteryOptimized = !PermissionHelper.isIgnoringBatteryOptimizations(context)
        isBtConnected = BluetoothHelper.isBluetoothAudioConnected(context)

        // Silent background check for updates from connected GitHub repo
        scope.launch(Dispatchers.IO) {
            try {
                val manager = AppModule.updateManager ?: AppUpdateManager.getInstance()
                val result = manager.checkForUpdate(context)
                if (result is UpdateStatus.UpdateAvailable) {
                    availableUpdate = result.updateInfo
                }
            } catch (_: Exception) {
                // Silently ignore network failures on home launch
            }
        }
    }


    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with App Title & Bluetooth Status
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SoundBox Pro",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (uiState.appSettings.isEnabled) Color(0xFF16A34A) else Color.Gray)
                            )
                            Text(
                                text = if (uiState.appSettings.isEnabled) "Service Active • Crash Protected" else "Paused",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Bluetooth Speaker Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isBtConnected) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                if (isBtConnected) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                                contentDescription = null,
                                tint = if (isBtConnected) Color(0xFF166534) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isBtConnected) "BT Speaker" else "Internal Audio",
                                color = if (isBtConnected) Color(0xFF166534) else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Missing Notification Permission Warning
            if (!hasNotificationPermission) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Notification Access Missing", fontWeight = FontWeight.Bold)
                                Text("App cannot detect payments without it.", style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }) {
                                Text("Fix Now")
                            }
                        }
                    }
                }
            }

            // Battery Optimization Warning
            if (isBatteryOptimized) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🛡️", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Keep Alive in Background", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text("Disable battery optimization so Android never stops your sound box.", style = MaterialTheme.typography.bodySmall)
                            }
                            FilledTonalButton(onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val fallback = Intent(Settings.ACTION_SETTINGS)
                                    context.startActivity(fallback)
                                }
                            }) {
                                Text("Protect")
                            }
                        }
                    }
                }
            }

            // GitHub In-App Update Banner
            if (availableUpdate != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToSettings() },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🚀", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Update Available: v${availableUpdate?.latestVersion}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "Tap to review changelog and download APK",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                            FilledTonalButton(onClick = onNavigateToSettings) {
                                Text("Update")
                            }
                        }
                    }
                }
            }

            // Service Power Toggle Hero

            item {
                StatusHeroCard(
                    isActive = uiState.appSettings.isEnabled,
                    onToggle = { viewModel.toggleServiceState(it) }
                )
            }

            // Merchant Quick Tools Row (Countertop Kiosk, Shop QR, Analytics)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToKiosk() },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF4ADE80))
                            Text("Kiosk Stand", fontWeight = FontWeight.Bold, color = Color.White, style = MaterialTheme.typography.titleSmall)
                            Text("Countertop Mode", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showQrDialog = true },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text("Shop QR", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("UPI QR Code", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToAnalytics() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Sales Chart", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text("Reports & CSV", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Today's Collection & Payments Count
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Today's Earnings",
                        value = "₹${String.format(Locale.US, "%,.2f", uiState.todayEarnings)}",
                        valueColor = MaterialTheme.colorScheme.secondary
                    )
                    SummaryCard(
                        modifier = Modifier.weight(1f),
                        title = "Payments Detected",
                        value = "${uiState.todayCount}",
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Last Payment Card with Replay Button & Day Voice Summary Button
            item {
                LastPaymentCard(
                    tx = uiState.lastPayment,
                    isReplaying = isReplayingVoice,
                    onReplay = { tx ->
                        scope.launch {
                            isReplayingVoice = true
                            AnnouncementHelper.replayTransaction(context, tx)
                            isReplayingVoice = false
                        }
                    },
                    onDaySummary = {
                        scope.launch {
                            isReplayingVoice = true
                            AnnouncementHelper.announceDaySummary(context, uiState.todayCount, uiState.todayEarnings)
                            isReplayingVoice = false
                        }
                    }
                )
            }

            // Quick Test Buttons
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("🔊 Test SoundBox Announcement", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text("Instant test of payment audio announcement & customer name speech", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.simulateTestPayment(100.0, null, "PhonePe") }
                            ) {
                                Text("₹100 (PhonePe)")
                            }
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.simulateTestPayment(500.0, "Rahul Sharma", "Google Pay") }
                            ) {
                                Text("₹500 (GPay)")
                            }
                        }
                    }
                }
            }

            // Live Activity Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Live Activity", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = onNavigateToHistory) {
                        Text("See All →")
                    }
                }
            }

            if (uiState.liveActivity.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No transactions yet. Waiting for payments...", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    // Shop QR Dialog
    if (showQrDialog) {
        ShopQrDialog(
            initialUpiId = uiState.appSettings.shopUpiId,
            initialShopName = uiState.appSettings.shopName,
            onSaveUpiDetails = { upi, name ->
                viewModel.updateShopUpi(upi, name)
            },
            onDismiss = { showQrDialog = false }
        )
    }
}

@Composable
fun StatusHeroCard(isActive: Boolean, onToggle: (Boolean) -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
        animationSpec = tween(300), label = ""
    )
    val contentColor = Color.White

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isActive) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isActive) "SoundBox Active" else "SoundBox Stopped",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = if (isActive) "Listening loudly for all UPI & Bank alerts" else "Tap here to turn on payment announcements",
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
            Switch(
                checked = isActive,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.secondary,
                    checkedTrackColor = Color.White,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.Red.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
fun SummaryCard(modifier: Modifier = Modifier, title: String, value: String, valueColor: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp).fillMaxWidth()) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = valueColor)
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📢 Last Payment Detected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (isReplaying) {
                    AudioEqualizerWave(isPlaying = true, tintColor = MaterialTheme.colorScheme.primary)
                }
            }

            if (tx != null) {
                Text(
                    tx.amountFormatted,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                val time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(tx.timestamp))
                Text("via ${tx.sourceAppName} • $time", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)

                tx.payerName?.let { name ->
                    Text("from $name", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                tx.refId?.let { ref ->
                    Text(
                        "UTR: $ref",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Action Buttons: Replay Last Voice & Day Summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onReplay(tx) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Replay (दोहराएं)", fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = onDaySummary,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("दिन का हिसाब", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text("No payments detected yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(
                    onClick = onDaySummary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Announce Day Summary (दिन का हिसाब)")
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
        AnnouncementStatus.ANNOUNCED.name -> MaterialTheme.colorScheme.secondary
        AnnouncementStatus.DUPLICATE_SKIPPED.name -> Color(0xFFFBBC04)
        else -> MaterialTheme.colorScheme.error
    }

    val statusText = when (tx.status) {
        AnnouncementStatus.ANNOUNCED.name -> "Announced"
        AnnouncementStatus.DUPLICATE_SKIPPED.name -> "Duplicate"
        else -> "Error"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(statusColor))
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(tx.amountFormatted, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                tx.payerName?.let {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text("via ${tx.sourceAppName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // UTR chip if present with 1-tap copy
            tx.refId?.let { ref ->
                Row(
                    modifier = Modifier
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("UTR", ref))
                            Toast.makeText(context, "Copied UTR: $ref", Toast.LENGTH_SHORT).show()
                        }
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "UTR: $ref",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
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
            Text(time, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(statusText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = statusColor)

                // Replay Icon Button
                IconButton(
                    onClick = onReplay,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "Replay",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
