package com.example.presentation.screens.kiosk

import android.app.Activity
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.presentation.screens.home.AudioEqualizerWave
import com.example.presentation.screens.home.HomeViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KioskScreen(
    onExit: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var isSpeaking by remember { mutableStateOf(false) }
    var showQrCode by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var currentTime by remember { mutableStateOf("") }
    var isBtConnected by remember { mutableStateOf(BluetoothHelper.isBluetoothAudioConnected(context)) }

    // Keep screen on while in Kiosk mode
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Live clock ticker
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
                qrBitmap = QrCodeGenerator.createQrBitmap(url, 500)
            }
        }
    }

    val latestTx = uiState.recentTransactions.firstOrNull { it.status == "ANNOUNCED" }

    Scaffold(
        containerColor = Color(0xFF0F172A), // Premium deep dark slate terminal background
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E293B),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(onClick = onExit) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Exit Kiosk")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E))
                        )
                        Text(
                            text = "Countertop Terminal",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                },
                actions = {
                    // Bluetooth Status Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isBtConnected) Color(0xFF166534) else Color(0xFF334155),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                if (isBtConnected) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                                contentDescription = null,
                                tint = if (isBtConnected) Color(0xFF86EFAC) else Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isBtConnected) "BT Speaker" else "Phone Audio",
                                color = if (isBtConnected) Color(0xFF86EFAC) else Color.LightGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // QR toggle button
                    if (uiState.appSettings.shopUpiId.isNotBlank()) {
                        IconButton(onClick = { showQrCode = !showQrCode }) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = "Toggle QR",
                                tint = if (showQrCode) Color(0xFF38BDF8) else Color.White
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Live clock bar
            Text(
                text = currentTime,
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            // Center Display: Either Large Last Payment Card or QR Code Card
            AnimatedVisibility(visible = showQrCode && qrBitmap != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.appSettings.shopName,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.Black
                        )
                        Text(
                            text = uiState.appSettings.shopUpiId,
                            color = Color.DarkGray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(12.dp))
                        qrBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "QR Code",
                                modifier = Modifier
                                    .size(240.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Scan with PhonePe, GPay, Paytm, BHIM",
                            color = Color(0xFF0F766E),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (!showQrCode) {
                // Giant High-Contrast Latest Payment Announcement Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "LATEST PAYMENT RECEIVED",
                                color = Color(0xFF4ADE80),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 1.sp
                            )
                            if (isSpeaking) {
                                AudioEqualizerWave(isPlaying = true, tintColor = Color(0xFF4ADE80))
                            }
                        }

                        if (latestTx != null) {
                            Text(
                                text = "₹${String.format(Locale.US, "%,.2f", latestTx.amount)}",
                                color = Color(0xFF22C55E),
                                fontWeight = FontWeight.Black,
                                fontSize = 48.sp,
                                fontFamily = FontFamily.SansSerif
                            )

                            latestTx.payerName?.let { name ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF0F766E).copy(alpha = 0.3f)
                                ) {
                                    Text(
                                        text = "From: $name",
                                        color = Color(0xFF2DD4BF),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Via ${latestTx.sourceAppName}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 13.sp
                                )
                                latestTx.refId?.let { ref ->
                                    Text(
                                        text = "UTR: $ref",
                                        color = Color(0xFF64748B),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "₹ 0.00",
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Bold,
                                fontSize = 44.sp
                            )
                            Text(
                                text = "Waiting for incoming payment...",
                                color = Color(0xFF94A3B8),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Bottom Section: Day Summary Bar & Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Today's Collection Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "TODAY'S TOTAL",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "₹${String.format(Locale.US, "%,.2f", uiState.todayTotalAmount)}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF334155)
                        ) {
                            Text(
                                text = "${uiState.todayTransactionCount} Payments",
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Action Buttons: Replay Last Voice & Day Summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (latestTx != null && !isSpeaking) {
                                scope.launch {
                                    isSpeaking = true
                                    AnnouncementHelper.replayTransaction(context, latestTx)
                                    isSpeaking = false
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF22C55E),
                            contentColor = Color.Black
                        ),
                        enabled = latestTx != null && !isSpeaking
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Replay (दोहराएं)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Button(
                        onClick = {
                            if (!isSpeaking) {
                                scope.launch {
                                    isSpeaking = true
                                    AnnouncementHelper.announceDaySummary(
                                        context,
                                        uiState.todayTransactionCount,
                                        uiState.todayTotalAmount
                                    )
                                    isSpeaking = false
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF38BDF8),
                            contentColor = Color.Black
                        ),
                        enabled = !isSpeaking
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Day Voice Report",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
