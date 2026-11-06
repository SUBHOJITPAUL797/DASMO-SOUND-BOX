package com.example.presentation.screens.applock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.di.AppModule
import com.example.presentation.components.PatternLockView
import com.example.presentation.components.PatternState
import com.example.presentation.components.PinLockView
import com.example.util.AppLockManager
import com.example.util.KnownPaymentApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val isPaymentApp: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSettingsScreen(
    onBack: () -> Unit,
    onNavigateToIntruderLogs: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = AppModule.settingsRepository
    val intruderRepo = AppModule.intruderRepository

    val settings by (settingsRepo?.getSettings() ?: remember { kotlinx.coroutines.flow.emptyFlow<com.example.domain.model.AppSettings>() })
        .collectAsState(initial = null)

    val intruderCount by (intruderRepo?.getLogCount() ?: remember { kotlinx.coroutines.flow.emptyFlow<Int>() })
        .collectAsState(initial = 0)

    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasUsageAccess by remember { mutableStateOf(AppLockManager.hasUsageAccessPermission(context)) }
    var hasOverlay by remember { mutableStateOf(AppLockManager.hasOverlayPermission(context)) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPerm = isGranted
    }

    var showPatternSetupDialog by remember { mutableStateOf(false) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }

    // Load installed apps in background
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val list = packages.mapNotNull { appInfo ->
                // Filter out non-launchable system apps
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isPayment = KnownPaymentApps.packageNames.containsKey(appInfo.packageName)
                if (isPayment || !isSystem || appInfo.packageName == context.packageName) {
                    val name = pm.getApplicationLabel(appInfo).toString()
                    InstalledAppItem(
                        packageName = appInfo.packageName,
                        appName = name,
                        isPaymentApp = isPayment
                    )
                } else null
            }.sortedWith(compareBy({ !it.isPaymentApp }, { it.appName.lowercase() }))

            withContext(Dispatchers.Main) {
                installedApps = list
                isLoadingApps = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("🛡️ App Lock & Security", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Master App Lock Switch
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
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
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Column {
                                Text(
                                    text = "Enable App Lock",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Lock DASMO Sound Box & payment apps",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = settings?.appLockEnabled ?: false,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    settingsRepo?.updateAppLockEnabled(enabled)
                                    if (enabled && settings?.appLockPattern.isNullOrBlank() && settings?.appLockPin.isNullOrBlank()) {
                                        // Prompt to setup lock type
                                        if (settings?.appLockType == "PIN") {
                                            showPinSetupDialog = true
                                        } else {
                                            showPatternSetupDialog = true
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Lock Type Selector (Pattern vs PIN)
            item {
                Text(
                    text = "🔐 LOCK METHOD",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Pattern Option Button
                            val isPattern = (settings?.appLockType ?: "PATTERN") == "PATTERN"
                            Button(
                                onClick = {
                                    scope.launch {
                                        settingsRepo?.updateAppLockType("PATTERN")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPattern) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isPattern) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.Pattern, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Pattern Lock", fontWeight = FontWeight.Bold)
                            }

                            // PIN Option Button
                            Button(
                                onClick = {
                                    scope.launch {
                                        settingsRepo?.updateAppLockType("PIN")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!isPattern) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (!isPattern) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("PIN Lock", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Configure/Change Pattern or PIN Button
                        val isPattern = (settings?.appLockType ?: "PATTERN") == "PATTERN"
                        val isConfigured = if (isPattern) {
                            !settings?.appLockPattern.isNullOrBlank()
                        } else {
                            !settings?.appLockPin.isNullOrBlank()
                        }

                        OutlinedButton(
                            onClick = {
                                if (isPattern) {
                                    showPatternSetupDialog = true
                                } else {
                                    showPinSetupDialog = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                if (isPattern) Icons.Default.Gesture else Icons.Default.Password,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isConfigured) "Change ${if (isPattern) "Pattern" else "PIN"}" else "Set ${if (isPattern) "Pattern" else "PIN"} Now",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Intruder Selfie & Logs Banner
            item {
                Text(
                    text = "📸 INTRUDER SELFIE SHIELD",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Capture Intruder Selfie",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "Take a front camera photo when wrong PIN or Pattern is entered",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings?.intruderSelfieEnabled ?: true,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        settingsRepo?.updateIntruderSelfieEnabled(enabled)
                                    }
                                }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // Banner to View Intruder Logs Screen
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToIntruderLogs() },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (intruderCount > 0) Color(0xFFEF4444).copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.PhotoCameraFront,
                                        contentDescription = null,
                                        tint = if (intruderCount > 0) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = "View Intruder Logs & Photos",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = if (intruderCount > 0) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "$intruderCount photo${if (intruderCount == 1) "" else "s"} logged",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    }
                }
            }

            // Permissions Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "⚙️ REQUIRED PERMISSIONS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Camera Permission
                        PermissionRow(
                            title = "Camera Permission (Intruder Selfie)",
                            isGranted = hasCameraPerm,
                            onGrant = { cameraLauncher.launch(Manifest.permission.CAMERA) }
                        )

                        // Usage Access Permission
                        PermissionRow(
                            title = "Usage Access (Detect Locked Apps)",
                            isGranted = hasUsageAccess,
                            onGrant = {
                                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                context.startActivity(intent)
                            }
                        )

                        // Overlay Permission
                        PermissionRow(
                            title = "Draw Over Other Apps (Lock Screen)",
                            isGranted = hasOverlay,
                            onGrant = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }

            // Apps to Lock Section
            item {
                Text(
                    text = "📱 SELECT APPS TO LOCK",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            }

            // Search bar for apps
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("Search installed apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true
                )
            }

            // Quick Toggle: All Payment Apps
            item {
                val lockedPackages = settings?.lockedAppPackages ?: emptySet()
                val paymentPackages = KnownPaymentApps.packageNames.keys
                val allPaymentsLocked = paymentPackages.all { lockedPackages.contains(it) }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                val updated = lockedPackages.toMutableSet()
                                if (allPaymentsLocked) {
                                    updated.removeAll(paymentPackages)
                                } else {
                                    updated.addAll(paymentPackages)
                                }
                                settingsRepo?.updateLockedAppPackages(updated)
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text("Lock All Payment Apps", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("PhonePe, Paytm, Google Pay, BHIM, etc.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Checkbox(
                            checked = allPaymentsLocked,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    val updated = lockedPackages.toMutableSet()
                                    if (checked) {
                                        updated.addAll(paymentPackages)
                                    } else {
                                        updated.removeAll(paymentPackages)
                                    }
                                    settingsRepo?.updateLockedAppPackages(updated)
                                }
                            }
                        )
                    }
                }
            }

            // List of apps
            val filteredApps = installedApps.filter {
                searchQuery.isBlank() || it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)
            }

            items(filteredApps, key = { it.packageName }) { app ->
                val lockedPackages = settings?.lockedAppPackages ?: emptySet()
                val isLocked = lockedPackages.contains(app.packageName)
                val appIcon = remember(app.packageName) { AppLockManager.getAppIcon(context, app.packageName) }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (appIcon != null) {
                                Image(
                                    bitmap = appIcon.toBitmap(80, 80).asImageBitmap(),
                                    contentDescription = app.appName,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(38.dp))
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = app.appName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (app.isPaymentApp) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "PAYMENT",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = app.packageName,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Switch(
                            checked = isLocked,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    val updated = lockedPackages.toMutableSet()
                                    if (checked) {
                                        updated.add(app.packageName)
                                    } else {
                                        updated.remove(app.packageName)
                                    }
                                    settingsRepo?.updateLockedAppPackages(updated)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Pattern Setup Dialog (Draw twice to confirm)
        if (showPatternSetupDialog) {
            PatternSetupDialog(
                onDismiss = { showPatternSetupDialog = false },
                onPatternConfirmed = { pattern ->
                    scope.launch {
                        settingsRepo?.updateAppLockPattern(pattern)
                        settingsRepo?.updateAppLockType("PATTERN")
                        showPatternSetupDialog = false
                    }
                }
            )
        }

        // PIN Setup Dialog (Enter twice to confirm)
        if (showPinSetupDialog) {
            PinSetupDialog(
                onDismiss = { showPinSetupDialog = false },
                onPinConfirmed = { pin ->
                    scope.launch {
                        settingsRepo?.updateAppLockPin(pin)
                        settingsRepo?.updateAppLockType("PIN")
                        showPinSetupDialog = false
                    }
                }
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                text = if (isGranted) "Granted ✓" else "Not Granted",
                fontSize = 11.sp,
                color = if (isGranted) Color(0xFF22C55E) else Color(0xFFEF4444),
                fontWeight = FontWeight.Bold
            )
        }

        if (!isGranted) {
            TextButton(onClick = onGrant) {
                Text("Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PatternSetupDialog(
    onDismiss: () -> Unit,
    onPatternConfirmed: (String) -> Unit
) {
    var step by remember { mutableIntStateOf(1) } // 1 = Draw, 2 = Confirm
    var firstPattern by remember { mutableStateOf("") }
    var patternState by remember { mutableStateOf(PatternState.NORMAL) }
    var message by remember { mutableStateOf("Draw a pattern (connect at least 4 dots)") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (step == 1) "Set Unlock Pattern" else "Confirm Your Pattern",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = message,
                    fontSize = 13.sp,
                    color = if (isError) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                PatternLockView(
                    size = 260.dp,
                    state = patternState,
                    onPatternStarted = {
                        patternState = PatternState.NORMAL
                        isError = false
                    },
                    onPatternCompleted = { pattern ->
                        if (step == 1) {
                            firstPattern = pattern
                            step = 2
                            message = "Draw the pattern again to confirm"
                            patternState = PatternState.NORMAL
                        } else {
                            if (pattern == firstPattern) {
                                patternState = PatternState.SUCCESS
                                message = "Pattern confirmed!"
                                onPatternConfirmed(pattern)
                            } else {
                                patternState = PatternState.ERROR
                                isError = true
                                message = "Patterns do not match. Try again."
                            }
                        }
                    },
                    onPatternTooShort = {
                        isError = true
                        message = "Connect at least 4 dots"
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    if (step == 2) {
                        TextButton(onClick = {
                            step = 1
                            firstPattern = ""
                            message = "Draw a pattern (connect at least 4 dots)"
                            patternState = PatternState.NORMAL
                            isError = false
                        }) {
                            Text("Reset")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinSetupDialog(
    onDismiss: () -> Unit,
    onPinConfirmed: (String) -> Unit
) {
    var step by remember { mutableIntStateOf(1) } // 1 = Enter PIN, 2 = Confirm PIN
    var firstPin by remember { mutableStateOf("") }
    var pinState by remember { mutableStateOf(PatternState.NORMAL) }
    var message by remember { mutableStateOf("Enter a 4-digit PIN") }
    var isError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (step == 1) "Set 4-Digit PIN" else "Confirm Your PIN",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = message,
                    fontSize = 13.sp,
                    color = if (isError) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                PinLockView(
                    pinLength = 4,
                    state = pinState,
                    onPinEntered = { pin ->
                        if (step == 1) {
                            firstPin = pin
                            step = 2
                            message = "Enter the 4-digit PIN again to confirm"
                            pinState = PatternState.NORMAL
                        } else {
                            if (pin == firstPin) {
                                pinState = PatternState.SUCCESS
                                message = "PIN confirmed!"
                                onPinConfirmed(pin)
                            } else {
                                pinState = PatternState.ERROR
                                isError = true
                                message = "PINs do not match. Try again."
                            }
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    if (step == 2) {
                        TextButton(onClick = {
                            step = 1
                            firstPin = ""
                            message = "Enter a 4-digit PIN"
                            pinState = PatternState.NORMAL
                            isError = false
                        }) {
                            Text("Reset")
                        }
                    }
                }
            }
        }
    }
}

