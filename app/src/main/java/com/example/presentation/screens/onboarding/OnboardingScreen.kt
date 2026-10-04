package com.example.presentation.screens.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.di.AppModule
import com.example.util.PermissionHelper
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(1) }
    var hasNotifPermission by remember { mutableStateOf(PermissionHelper.isNotificationListenerEnabled(context)) }
    var hasBatteryPermission by remember { mutableStateOf(PermissionHelper.isIgnoringBatteryOptimizations(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifPermission = PermissionHelper.isNotificationListenerEnabled(context)
                hasBatteryPermission = PermissionHelper.isIgnoringBatteryOptimizations(context)
                
                if (step == 2 && hasNotifPermission) {
                    step = 3
                }
                if (step == 3 && hasBatteryPermission) {
                    scope.launch {
                        AppModule.settingsRepository?.updateHasCompletedOnboarding(true)
                        onComplete()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(step) {
        hasNotifPermission = PermissionHelper.isNotificationListenerEnabled(context)
        hasBatteryPermission = PermissionHelper.isIgnoringBatteryOptimizations(context)
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (step) {
                1 -> {
                    Text("Turn Your Phone Into a Sound Box", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text("Hear every UPI payment — instantly, loudly, every time.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) {
                        Text("Get Started →")
                    }
                }
                2 -> {
                    Text("Allow Notification Access", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text("SoundBox Pro needs to read payment notifications from PhonePe, Google Pay, Paytm, and all other UPI apps on your phone.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(32.dp))
                    
                    if (hasNotifPermission) {
                        Text("Permission Granted! ✅", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { step = 3 }, modifier = Modifier.fillMaxWidth()) {
                            Text("Next →")
                        }
                    } else {
                        Button(onClick = { 
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Grant Notification Access")
                        }
                    }
                }
                3 -> {
                    Text("Run in Background", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text("To announce payments automatically, disable battery optimizations. Also, if your phone has an 'Auto-start' manager, allow this app.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(32.dp))
                    
                    if (hasBatteryPermission) {
                        Text("Permission Granted! ✅", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { 
                            scope.launch {
                                AppModule.settingsRepository?.updateHasCompletedOnboarding(true)
                                onComplete()
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Continue to Dashboard →")
                        }
                    } else {
                        Button(onClick = { 
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Allow Background Execution")
                        }
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = {
                            scope.launch {
                                AppModule.settingsRepository?.updateHasCompletedOnboarding(true)
                                onComplete()
                            }
                        }) {
                            Text("Skip for now")
                        }
                    }
                }
            }
        }
    }
}
