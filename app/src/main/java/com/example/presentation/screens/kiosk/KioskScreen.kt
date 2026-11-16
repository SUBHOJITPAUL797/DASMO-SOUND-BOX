package com.example.presentation.screens.kiosk

import android.app.Activity
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.presentation.screens.home.AudioEqualizerWave
import com.example.presentation.screens.home.HomeViewModel
import com.example.ui.theme.LocalIsDarkTheme
import com.example.util.AnnouncementHelper
import com.example.util.BluetoothHelper
import com.example.util.QrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// True AMOLED Pitch-Black Palette (0x000000 keeps OLED/AMOLED subpixels completely OFF)
private val AmoledBlack = Color(0xFF000000)
private val AmoledHairline = Color(0xFF1A222D)
private val AmoledEmeraldRim = Color(0xFF0D4A32)
private val AmoledEmerald = Color(0xFF22C55E)
private val AmoledEmeraldSoft = Color(0xFF4ADE80)
private val AmoledCyan = Color(0xFF38BDF8)
private val AmoledCyanRim = Color(0xFF0C4A6E)
private val AmoledAmber = Color(0xFFF59E0B)
private val AmoledAmberRim = Color(0xFF78350F)
private val AmoledTextPrimary = Color(0xFFE5E7EB)
private val AmoledTextMuted = Color(0xFF8B98A9)
private val AmoledTextSubtle = Color(0xFF526071)

private enum class KioskCenterMode {
    LATEST_PAYMENT,
    QR_COUNTER
}

private enum class KioskSpeechAction {
    IDLE,
    REPLAYING_PAYMENT,
    SPEAKING_SUMMARY
}

private enum class KioskBatteryNoticeState {
    PENDING_SETTINGS,
    VISIBLE,
    DISMISSED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskScreen(
    onExit: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isAppDarkTheme = LocalIsDarkTheme.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var speechAction by remember { mutableStateOf(KioskSpeechAction.IDLE) }
    var centerMode by remember { mutableStateOf(KioskCenterMode.LATEST_PAYMENT) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentTime by remember { mutableStateOf("") }
    var isBtConnected by remember { mutableStateOf(BluetoothHelper.isBluetoothAudioConnected(context)) }
    val isSpeaking = speechAction != KioskSpeechAction.IDLE

    // Keep screen awake AND enforce light-on-black system bar icons while in AMOLED Kiosk mode
    DisposableEffect(view, isAppDarkTheme) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val insetsController = if (window != null && !view.isInEditMode) {
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        } else null

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController?.apply {
                isAppearanceLightStatusBars = !isAppDarkTheme
                isAppearanceLightNavigationBars = !isAppDarkTheme
            }
        }
    }

    // Live clock & Bluetooth audio status ticker
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("hh:mm:ss a • EEE, dd MMM", Locale.getDefault())
        while (true) {
            currentTime = timeFormat.format(Date())
            isBtConnected = BluetoothHelper.isBluetoothAudioConnected(context)
            delay(1000)
        }
    }

    // QR bitmap generation
    LaunchedEffect(uiState.appSettings.shopUpiId, uiState.appSettings.shopName) {
        val upiId = uiState.appSettings.shopUpiId
        if (upiId.isNotBlank()) {
            val url = QrCodeGenerator.generateUpiUrl(upiId, uiState.appSettings.shopName)
            withContext(Dispatchers.Default) {
                qrBitmap = QrCodeGenerator.createQrBitmap(url, 460)
            }
        } else {
            qrBitmap = null
            centerMode = KioskCenterMode.LATEST_PAYMENT
        }
    }

    val latestTx = uiState.recentTransactions.firstOrNull { it.status == "ANNOUNCED" }
    val batteryNoticeState = when {
        !uiState.isLoaded -> KioskBatteryNoticeState.PENDING_SETTINGS
        !uiState.appSettings.hasSeenKioskBatteryNotice -> KioskBatteryNoticeState.VISIBLE
        else -> KioskBatteryNoticeState.DISMISSED
    }

    if (batteryNoticeState == KioskBatteryNoticeState.VISIBLE) {
        KioskBatteryNoticeDialog(
            onDismiss = { viewModel.markKioskBatteryNoticeSeen() }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = AmoledBlack,
        topBar = {
            Column(modifier = Modifier.background(AmoledBlack)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AmoledBlack,
                        titleContentColor = AmoledTextPrimary,
                        navigationIconContentColor = AmoledTextPrimary,
                        actionIconContentColor = AmoledTextPrimary
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = onExit,
                            modifier = Modifier.testTag("kiosk_exit_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit Kiosk")
                        }
                    },
                    title = {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(AmoledEmerald)
                                )
                                Text(
                                    text = "Countertop Terminal",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AmoledTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "AMOLED PITCH-BLACK • ECO",
                                color = AmoledTextSubtle,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    },
                    actions = {
                        // Outlined AMOLED Bluetooth Status Pill (0% background fill)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AmoledBlack,
                            border = BorderStroke(
                                1.dp,
                                if (isBtConnected) AmoledEmeraldRim else AmoledHairline
                            ),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    if (isBtConnected) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                                    contentDescription = if (isBtConnected) "Bluetooth audio active" else "Phone speaker active",
                                    tint = if (isBtConnected) AmoledEmeraldSoft else AmoledTextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isBtConnected) "BT Speaker" else "Phone Audio",
                                    color = if (isBtConnected) AmoledEmeraldSoft else AmoledTextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (uiState.appSettings.shopUpiId.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    centerMode = if (centerMode == KioskCenterMode.QR_COUNTER) {
                                        KioskCenterMode.LATEST_PAYMENT
                                    } else {
                                        KioskCenterMode.QR_COUNTER
                                    }
                                },
                                modifier = Modifier.testTag("kiosk_qr_toggle_btn")
                            ) {
                                Icon(
                                    Icons.Default.QrCode,
                                    contentDescription = if (centerMode == KioskCenterMode.QR_COUNTER) "Show Latest Payment" else "Show Shop QR",
                                    tint = if (centerMode == KioskCenterMode.QR_COUNTER) AmoledCyan else AmoledTextPrimary
                                )
                            }
                        }
                    }
                )
                HorizontalDivider(thickness = 1.dp, color = AmoledHairline)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AmoledBlack)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Status Strip: Shop Identity + Live Clock
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = AmoledBlack,
                    border = BorderStroke(1.dp, AmoledHairline)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            tint = AmoledEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = uiState.appSettings.shopName.ifBlank { "My Shop" },
                            color = AmoledTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = currentTime,
                    color = AmoledTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(12.dp))

            // Center Display: Either AMOLED Compact QR Card OR Pitch-Black Latest Payment Hero
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = centerMode == KioskCenterMode.QR_COUNTER && qrBitmap != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("kiosk_qr_card"),
                        shape = RoundedCornerShape(26.dp),
                        color = AmoledBlack,
                        border = BorderStroke(1.dp, AmoledHairline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = uiState.appSettings.shopName.ifBlank { "My Shop" },
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = AmoledTextPrimary
                            )
                            Text(
                                text = uiState.appSettings.shopUpiId,
                                color = AmoledTextMuted,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.height(4.dp))
                            // Tight quiet-zone ONLY around the QR code so ~85% of pixels remain #000000
                            qrBitmap?.let { bmp ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White)
                                        .padding(8.dp)
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "UPI QR Code",
                                        modifier = Modifier.size(196.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Scan with PhonePe, GPay, Paytm, BHIM",
                                color = AmoledEmeraldSoft,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = centerMode == KioskCenterMode.LATEST_PAYMENT,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("kiosk_payment_card"),
                        shape = RoundedCornerShape(28.dp),
                        color = AmoledBlack,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (latestTx != null) AmoledEmeraldRim else AmoledHairline
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (latestTx != null) AmoledEmeraldSoft else AmoledTextSubtle)
                                )
                                Text(
                                    text = "LATEST PAYMENT RECEIVED",
                                    color = if (latestTx != null) AmoledEmeraldSoft else AmoledTextMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.2.sp
                                )
                                if (isSpeaking) {
                                    AudioEqualizerWave(isPlaying = true, tintColor = AmoledEmeraldSoft)
                                }
                            }

                            if (latestTx != null) {
                                Text(
                                    text = "₹${String.format(Locale.US, "%,.2f", latestTx.amount)}",
                                    color = AmoledEmerald,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 50.sp,
                                    fontFamily = FontFamily.SansSerif
                                )

                                latestTx.payerName?.takeIf { it.isNotBlank() }?.let { name ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = AmoledBlack,
                                        border = BorderStroke(1.dp, Color(0xFF134E4A))
                                    ) {
                                        Text(
                                            text = "From: $name",
                                            color = Color(0xFF2DD4BF),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Via ${latestTx.sourceAppName}",
                                        color = AmoledTextMuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    latestTx.refId?.takeIf { it.isNotBlank() }?.let { ref ->
                                        Text(
                                            text = "•",
                                            color = AmoledTextSubtle,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "UTR: $ref",
                                            color = AmoledTextSubtle,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "₹ 0.00",
                                    color = Color(0xFF334155),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 46.sp
                                )
                                Text(
                                    text = "Waiting for incoming payment...",
                                    color = AmoledTextMuted,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Pixels kept pitch-black to save OLED battery",
                                    color = AmoledTextSubtle,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Bottom Section: AMOLED Day Summary Bar & Outlined Pitch-Black Action Buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = AmoledBlack,
                    border = BorderStroke(1.dp, AmoledHairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "TODAY'S TOTAL",
                                color = AmoledTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "₹${String.format(Locale.US, "%,.2f", uiState.todayTotalAmount)}",
                                color = AmoledTextPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AmoledBlack,
                            border = BorderStroke(1.dp, AmoledCyanRim)
                        ) {
                            Text(
                                text = "${uiState.todayTransactionCount} Payments",
                                color = AmoledCyan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Action Buttons: Pure Black fill with high-contrast AMOLED rims
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val replayEnabled = latestTx != null && !isSpeaking
                    OutlinedButton(
                        onClick = {
                            if (latestTx != null && speechAction == KioskSpeechAction.IDLE) {
                                scope.launch {
                                    speechAction = KioskSpeechAction.REPLAYING_PAYMENT
                                    try {
                                        AnnouncementHelper.replayTransaction(context, latestTx)
                                    } finally {
                                        speechAction = KioskSpeechAction.IDLE
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("kiosk_replay_btn"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = if (replayEnabled) AmoledEmerald else AmoledHairline
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = AmoledBlack,
                            contentColor = AmoledEmeraldSoft,
                            disabledContainerColor = AmoledBlack,
                            disabledContentColor = AmoledTextSubtle
                        ),
                        enabled = replayEnabled
                    ) {
                        Icon(
                            Icons.Default.Replay,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Replay",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (speechAction == KioskSpeechAction.IDLE) {
                                scope.launch {
                                    speechAction = KioskSpeechAction.SPEAKING_SUMMARY
                                    try {
                                        AnnouncementHelper.announceDaySummary(
                                            context,
                                            uiState.todayTransactionCount,
                                            uiState.todayTotalAmount
                                        )
                                    } finally {
                                        speechAction = KioskSpeechAction.IDLE
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("kiosk_day_report_btn"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = if (!isSpeaking) AmoledCyan else AmoledHairline
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = AmoledBlack,
                            contentColor = AmoledCyan,
                            disabledContainerColor = AmoledBlack,
                            disabledContentColor = AmoledTextSubtle
                        ),
                        enabled = !isSpeaking
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Day Report",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KioskBatteryNoticeDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("kiosk_battery_warning_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = AmoledBlack,
            border = BorderStroke(1.dp, AmoledAmberRim)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .border(1.dp, AmoledAmberRim, CircleShape)
                        .background(AmoledBlack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = AmoledAmber,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Text(
                    text = "AMOLED Battery-Saver Display",
                    color = AmoledTextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Kiosk Stand keeps your display awake using true pitch-black (#000000) pixels so AMOLED & OLED screens stay cool and save battery.",
                    color = AmoledTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AmoledBlack,
                    border = BorderStroke(1.dp, AmoledAmberRim),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Tip for Non-AMOLED / LCD phones: If your screen is not AMOLED or OLED, keeping the display on continuously will still use battery — lower brightness or plug in a charger during long shifts.",
                        color = AmoledAmber,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("kiosk_battery_warning_confirm_btn"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, AmoledEmerald),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = AmoledBlack,
                        contentColor = AmoledEmeraldSoft
                    )
                ) {
                    Text(
                        text = "Got it, Continue",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
