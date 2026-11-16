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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.di.AppModule
import com.example.presentation.components.PatternLockView
import com.example.presentation.components.PatternState
import com.example.presentation.components.PinLockView
import com.example.presentation.components.PasswordLockView
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

data class AppLockSetupRequest(
    val method: AppLockMethod,
    val enableLockOnConfirm: Boolean = false
)

private sealed interface SecretSetupPhase {
    val message: String
    val patternState: PatternState
    val isError: Boolean

    data class EnterFirst(
        override val message: String,
        override val patternState: PatternState = PatternState.NORMAL,
        override val isError: Boolean = false
    ) : SecretSetupPhase

    data class ConfirmSecond(
        val firstSecret: String,
        override val message: String,
        override val patternState: PatternState = PatternState.NORMAL,
        override val isError: Boolean = false
    ) : SecretSetupPhase

    data class Confirmed(
        val finalSecret: String,
        override val message: String
    ) : SecretSetupPhase {
        override val patternState: PatternState = PatternState.SUCCESS
        override val isError: Boolean = false
    }
}

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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                hasUsageAccess = AppLockManager.hasUsageAccessPermission(context)
                hasOverlay = AppLockManager.hasOverlayPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPerm = isGranted
    }

    var activeSetupRequest by remember { mutableStateOf<AppLockSetupRequest?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }

    // Load installed apps in background
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val list = try {
                val pm = context.packageManager
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                packages.mapNotNull { appInfo ->
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
            } catch (_: Exception) {
                emptyList()
            }

            withContext(Dispatchers.Main) {
                installedApps = list
                isLoadingApps = false
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                                    if (!enabled) {
                                        activeSetupRequest = null
                                        settingsRepo?.updateAppLockEnabled(false)
                                    } else {
                                        val hasCredentials = settings?.let { AppLockManager.hasConfiguredCredentials(it) } == true
                                        if (hasCredentials) {
                                            settingsRepo?.updateAppLockEnabled(true)
                                            com.example.service.SoundBoxForegroundService.start(context)
                                        } else {
                                            activeSetupRequest = AppLockSetupRequest(
                                                method = AppLockMethod.fromKey(settings?.appLockType),
                                                enableLockOnConfirm = true
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Lock Type Selector (Pattern vs PIN vs Password)
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
                        val currentType = settings?.appLockType ?: "PATTERN"
                        val hasPattern = !settings?.appLockPattern.isNullOrBlank()
                        val hasPin = !settings?.appLockPin.isNullOrBlank()
                        val hasPassword = !settings?.appLockPassword.isNullOrBlank()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Pattern Option Button
                            val isPattern = currentType == "PATTERN"
                            Button(
                                onClick = {
                                    scope.launch {
                                        settingsRepo?.updateAppLockType("PATTERN")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPattern) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isPattern) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.Pattern, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Pattern", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            // PIN Option Button
                            val isPin = currentType == "PIN"
                            Button(
                                onClick = {
                                    scope.launch {
                                        settingsRepo?.updateAppLockType("PIN")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isPin) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("PIN", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            // Password Option Button
                            val isPassword = currentType == "PASSWORD"
                            Button(
                                onClick = {
                                    scope.launch {
                                        settingsRepo?.updateAppLockType("PASSWORD")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPassword) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isPassword) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.Password, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Password", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        // Method status indicators
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LockStatusChip("Pattern", hasPattern)
                            Text("•", color = MaterialTheme.colorScheme.outline)
                            LockStatusChip("PIN", hasPin)
                            Text("•", color = MaterialTheme.colorScheme.outline)
                            LockStatusChip("Password", hasPassword)
                        }

                        // Configure/Change Active Method Button
                        val isConfigured = when (currentType) {
                            "PIN" -> hasPin
                            "PASSWORD" -> hasPassword
                            else -> hasPattern
                        }
                        val typeName = when (currentType) {
                            "PIN" -> "PIN"
                            "PASSWORD" -> "Password"
                            else -> "Pattern"
                        }

                        OutlinedButton(
                            onClick = {
                                activeSetupRequest = AppLockSetupRequest(method = AppLockMethod.fromKey(currentType))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                when (currentType) {
                                    "PIN" -> Icons.Default.Pin
                                    "PASSWORD" -> Icons.Default.Password
                                    else -> Icons.Default.Gesture
                                },
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isConfigured) "Change $typeName" else "Set $typeName Now",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Option to configure any other method directly
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Setup other methods: ",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (currentType != "PATTERN") {
                                TextButton(
                                    onClick = { activeSetupRequest = AppLockSetupRequest(AppLockMethod.PATTERN) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Pattern", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (currentType != "PIN") {
                                TextButton(
                                    onClick = { activeSetupRequest = AppLockSetupRequest(AppLockMethod.PIN) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("PIN", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (currentType != "PASSWORD") {
                                TextButton(
                                    onClick = { activeSetupRequest = AppLockSetupRequest(AppLockMethod.PASSWORD) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text("Password", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
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
                                try {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            }
                        )

                        // Overlay Permission
                        PermissionRow(
                            title = "Draw Over Other Apps (Lock Screen)",
                            isGranted = hasOverlay,
                            onGrant = {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                    } catch (_: Exception) {}
                                }
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

        // Credential Setup Dialog driven by single AppLockSetupRequest state machine
        activeSetupRequest?.let { request ->
            val handleCredentialConfirmed: (String) -> Unit = { secret ->
                scope.launch {
                    when (request.method) {
                        AppLockMethod.PATTERN -> settingsRepo?.updateAppLockPattern(secret)
                        AppLockMethod.PIN -> settingsRepo?.updateAppLockPin(secret)
                        AppLockMethod.PASSWORD -> settingsRepo?.updateAppLockPassword(secret)
                    }
                    settingsRepo?.updateAppLockType(request.method.serializedKey)
                    if (request.enableLockOnConfirm) {
                        settingsRepo?.updateAppLockEnabled(true)
                        com.example.service.SoundBoxForegroundService.start(context)
                    }
                    activeSetupRequest = null
                }
            }

            when (request.method) {
                AppLockMethod.PATTERN -> PatternSetupDialog(
                    onDismiss = { activeSetupRequest = null },
                    onPatternConfirmed = handleCredentialConfirmed
                )
                AppLockMethod.PIN -> PinSetupDialog(
                    onDismiss = { activeSetupRequest = null },
                    onPinConfirmed = handleCredentialConfirmed
                )
                AppLockMethod.PASSWORD -> PasswordSetupDialog(
                    onDismiss = { activeSetupRequest = null },
                    onPasswordConfirmed = handleCredentialConfirmed
                )
            }
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
    val initialPrompt = "Draw a pattern (connect at least 4 dots)"
    var setupPhase by remember {
        mutableStateOf<SecretSetupPhase>(SecretSetupPhase.EnterFirst(initialPrompt))
    }

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
                    text = if (setupPhase is SecretSetupPhase.EnterFirst) "Set Unlock Pattern" else "Confirm Your Pattern",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = setupPhase.message,
                    fontSize = 13.sp,
                    color = if (setupPhase.isError) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                PatternLockView(
                    size = 260.dp,
                    state = setupPhase.patternState,
                    enabled = setupPhase !is SecretSetupPhase.Confirmed,
                    onPatternStarted = {
                        setupPhase = when (val phase = setupPhase) {
                            is SecretSetupPhase.EnterFirst -> phase.copy(
                                patternState = PatternState.NORMAL,
                                isError = false
                            )
                            is SecretSetupPhase.ConfirmSecond -> phase.copy(
                                patternState = PatternState.NORMAL,
                                isError = false
                            )
                            is SecretSetupPhase.Confirmed -> phase
                        }
                    },
                    onPatternCompleted = { pattern ->
                        when (val phase = setupPhase) {
                            is SecretSetupPhase.EnterFirst -> {
                                setupPhase = SecretSetupPhase.ConfirmSecond(
                                    firstSecret = pattern,
                                    message = "Draw the pattern again to confirm"
                                )
                            }
                            is SecretSetupPhase.ConfirmSecond -> {
                                if (pattern == phase.firstSecret) {
                                    setupPhase = SecretSetupPhase.Confirmed(pattern, "Pattern confirmed!")
                                    onPatternConfirmed(pattern)
                                } else {
                                    setupPhase = phase.copy(
                                        patternState = PatternState.ERROR,
                                        isError = true,
                                        message = "Patterns do not match. Try again."
                                    )
                                }
                            }
                            is SecretSetupPhase.Confirmed -> Unit
                        }
                    },
                    onPatternTooShort = {
                        setupPhase = when (val phase = setupPhase) {
                            is SecretSetupPhase.EnterFirst -> phase.copy(
                                isError = true,
                                message = "Connect at least 4 dots"
                            )
                            is SecretSetupPhase.ConfirmSecond -> phase.copy(
                                isError = true,
                                message = "Connect at least 4 dots"
                            )
                            is SecretSetupPhase.Confirmed -> phase
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
                    if (setupPhase is SecretSetupPhase.ConfirmSecond) {
                        TextButton(onClick = {
                            setupPhase = SecretSetupPhase.EnterFirst(initialPrompt)
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
    val initialPrompt = "Enter a 4-digit PIN"
    var setupPhase by remember {
        mutableStateOf<SecretSetupPhase>(SecretSetupPhase.EnterFirst(initialPrompt))
    }

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
                    text = if (setupPhase is SecretSetupPhase.EnterFirst) "Set 4-Digit PIN" else "Confirm Your PIN",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = setupPhase.message,
                    fontSize = 13.sp,
                    color = if (setupPhase.isError) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                PinLockView(
                    pinLength = 4,
                    state = setupPhase.patternState,
                    onPinEntered = { pin ->
                        when (val phase = setupPhase) {
                            is SecretSetupPhase.EnterFirst -> {
                                setupPhase = SecretSetupPhase.ConfirmSecond(
                                    firstSecret = pin,
                                    message = "Enter the 4-digit PIN again to confirm"
                                )
                            }
                            is SecretSetupPhase.ConfirmSecond -> {
                                if (pin == phase.firstSecret) {
                                    setupPhase = SecretSetupPhase.Confirmed(pin, "PIN confirmed!")
                                    onPinConfirmed(pin)
                                } else {
                                    setupPhase = phase.copy(
                                        patternState = PatternState.ERROR,
                                        isError = true,
                                        message = "PINs do not match. Try again."
                                    )
                                }
                            }
                            is SecretSetupPhase.Confirmed -> Unit
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
                    if (setupPhase is SecretSetupPhase.ConfirmSecond) {
                        TextButton(onClick = {
                            setupPhase = SecretSetupPhase.EnterFirst(initialPrompt)
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
private fun LockStatusChip(name: String, isConfigured: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = if (isConfigured) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isConfigured) Color(0xFF22C55E) else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = "$name ${if (isConfigured) "set" else "not set"}",
            fontSize = 11.sp,
            fontWeight = if (isConfigured) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isConfigured) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PasswordSetupDialog(
    onDismiss: () -> Unit,
    onPasswordConfirmed: (String) -> Unit
) {
    val initialPrompt = "Enter a password (at least 4 characters)"
    val confirmPrompt = "Enter the password again to confirm"
    var setupPhase by remember {
        mutableStateOf<SecretSetupPhase>(SecretSetupPhase.EnterFirst(initialPrompt))
    }
    val isConfirmStep = setupPhase is SecretSetupPhase.ConfirmSecond

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
                    text = if (setupPhase is SecretSetupPhase.EnterFirst) "Set Unlock Password" else "Confirm Your Password",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = setupPhase.message,
                    fontSize = 13.sp,
                    color = if (setupPhase.isError) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Keyed by stage so advancing from EnterFirst to ConfirmSecond cleanly clears the input field
                key(isConfirmStep) {
                    PasswordLockView(
                        state = setupPhase.patternState,
                        enabled = setupPhase !is SecretSetupPhase.Confirmed,
                        label = if (!isConfirmStep) "New Password" else "Confirm Password",
                        placeholder = if (!isConfirmStep) "Enter password" else "Re-enter password",
                        buttonText = if (!isConfirmStep) "Next" else "Confirm",
                        onPasswordChanged = {
                            if (setupPhase.isError) {
                                setupPhase = when (val phase = setupPhase) {
                                    is SecretSetupPhase.EnterFirst -> SecretSetupPhase.EnterFirst(initialPrompt)
                                    is SecretSetupPhase.ConfirmSecond -> phase.copy(
                                        message = confirmPrompt,
                                        patternState = PatternState.NORMAL,
                                        isError = false
                                    )
                                    is SecretSetupPhase.Confirmed -> phase
                                }
                            }
                        },
                        onPasswordEntered = { enteredPassword ->
                            when (val phase = setupPhase) {
                                is SecretSetupPhase.EnterFirst -> {
                                    if (enteredPassword.length < 4) {
                                        setupPhase = phase.copy(
                                            patternState = PatternState.ERROR,
                                            isError = true,
                                            message = "Password must be at least 4 characters"
                                        )
                                    } else {
                                        setupPhase = SecretSetupPhase.ConfirmSecond(
                                            firstSecret = enteredPassword,
                                            message = confirmPrompt
                                        )
                                    }
                                }
                                is SecretSetupPhase.ConfirmSecond -> {
                                    if (enteredPassword == phase.firstSecret) {
                                        setupPhase = SecretSetupPhase.Confirmed(
                                            finalSecret = enteredPassword,
                                            message = "Password confirmed!"
                                        )
                                        onPasswordConfirmed(enteredPassword)
                                    } else {
                                        setupPhase = phase.copy(
                                            patternState = PatternState.ERROR,
                                            isError = true,
                                            message = "Passwords do not match. Try again."
                                        )
                                    }
                                }
                                is SecretSetupPhase.Confirmed -> Unit
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    if (isConfirmStep) {
                        TextButton(onClick = {
                            setupPhase = SecretSetupPhase.EnterFirst(initialPrompt)
                        }) {
                            Text("Reset")
                        }
                    }
                }
            }
        }
    }
}

