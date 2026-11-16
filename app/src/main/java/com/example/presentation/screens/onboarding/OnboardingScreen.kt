package com.example.presentation.screens.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.di.AppModule
import com.example.ui.components.Floating3DObjectBox
import com.example.ui.components.ThreeDActionButton
import com.example.ui.components.ThreeDTactileCard
import com.example.ui.theme.AppSemanticColors
import com.example.ui.theme.BrandEmerald
import com.example.ui.theme.BrandEmeraldDark
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.LocalIsDarkTheme
import com.example.util.PermissionHelper
import kotlinx.coroutines.launch

enum class OnboardingStep(val order: Int) {
    WELCOME(1),
    NOTIFICATION_ACCESS(2),
    BATTERY_OPTIMIZATION(3)
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var isCompleting by remember { mutableStateOf(false) }
    var hasNotifPermission by remember { mutableStateOf(PermissionHelper.isNotificationListenerEnabled(context)) }
    var hasBatteryPermission by remember { mutableStateOf(PermissionHelper.isIgnoringBatteryOptimizations(context)) }

    fun completeOnboardingOnce() {
        if (isCompleting) return
        isCompleting = true
        scope.launch {
            AppModule.settingsRepository?.updateHasCompletedOnboarding(true)
            onComplete()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotifPermission = PermissionHelper.isNotificationListenerEnabled(context)
                hasBatteryPermission = PermissionHelper.isIgnoringBatteryOptimizations(context)

                if (step == OnboardingStep.NOTIFICATION_ACCESS && hasNotifPermission) {
                    step = OnboardingStep.BATTERY_OPTIMIZATION
                }
                if (step == OnboardingStep.BATTERY_OPTIMIZATION && hasBatteryPermission) {
                    completeOnboardingOnce()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(step) {
        hasNotifPermission = PermissionHelper.isNotificationListenerEnabled(context)
        hasBatteryPermission = PermissionHelper.isIgnoringBatteryOptimizations(context)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Step Progress Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OnboardingStep.entries.forEach { candidate ->
                    val active = candidate.order <= step.order
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(if (active) BrandIndigo else MaterialTheme.colorScheme.outline)
                    )
                }
            }

            // Animated 3D Center Stage
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(tween(200)) { -it / 3 } + fadeOut(tween(180)))
                },
                label = "onboardingStep",
                modifier = Modifier.weight(1f)
            ) { currentStep ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val isDark = LocalIsDarkTheme.current
                    when (currentStep) {
                        OnboardingStep.WELCOME -> {
                            Floating3DObjectBox(bobDistanceDp = 6f) {
                                Surface(
                                    shape = RoundedCornerShape(32.dp),
                                    color = if (isDark) Color(0xFF0D1117) else MaterialTheme.colorScheme.surface,
                                    shadowElevation = 12.dp,
                                    modifier = Modifier.size(210.dp)
                                ) {
                                    Image(
                                        painter = painterResource(
                                            id = if (isDark) R.drawable.img_3d_soundbox_hero_dark else R.drawable.img_3d_soundbox_hero
                                        ),
                                        contentDescription = "3D SoundBox Speaker",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Spacer(Modifier.height(32.dp))
                            Text(
                                "Turn Your Phone Into a Smart SoundBox",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Hear every PhonePe, Google Pay, Paytm, BHIM & Bank UPI credit loudly — with zero spam.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OnboardingStep.NOTIFICATION_ACCESS -> {
                            ThreeDTactileCard(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                depthColor = BrandIndigo.copy(alpha = 0.35f),
                                cornerRadius = 28.dp
                            ) {
                                Box(
                                    modifier = Modifier.padding(28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = AppSemanticColors.indigoText,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(28.dp))
                            Text(
                                "Allow Notification Access",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "SoundBox Pro reads payment receipts from your UPI apps locally on your device. Chats & loan spam are ignored.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hasNotifPermission) {
                                Spacer(Modifier.height(16.dp))
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AppSemanticColors.emeraldText, modifier = Modifier.size(18.dp))
                                        Text("Access Granted", color = AppSemanticColors.emeraldText, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        OnboardingStep.BATTERY_OPTIMIZATION -> {
                            ThreeDTactileCard(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                depthColor = BrandEmerald.copy(alpha = 0.35f),
                                cornerRadius = 28.dp
                            ) {
                                Box(
                                    modifier = Modifier.padding(28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.BatteryChargingFull,
                                        contentDescription = null,
                                        tint = AppSemanticColors.emeraldText,
                                        modifier = Modifier.size(64.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(28.dp))
                            Text(
                                "Keep Active 24×7",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Allow unrestricted background execution so Android never pauses your shop SoundBox.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Bottom Tactile 3D CTA
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (step) {
                    OnboardingStep.WELCOME -> {
                        ThreeDActionButton(
                            onClick = { step = OnboardingStep.NOTIFICATION_ACCESS },
                            modifier = Modifier.fillMaxWidth(),
                            height = 54.dp,
                            containerColor = BrandIndigo,
                            depthColor = Color(0xFF3730A3)
                        ) {
                            Text("Get Started", fontWeight = FontWeight.Bold, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
                        }
                    }
                    OnboardingStep.NOTIFICATION_ACCESS -> {
                        if (hasNotifPermission) {
                            ThreeDActionButton(
                                onClick = { step = OnboardingStep.BATTERY_OPTIMIZATION },
                                modifier = Modifier.fillMaxWidth(),
                                height = 54.dp,
                                containerColor = BrandEmerald,
                                depthColor = BrandEmeraldDark
                            ) {
                                Text("Continue", fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White)
                            }
                        } else {
                            ThreeDActionButton(
                                onClick = {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                    } catch (_: Exception) {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                        } catch (_: Exception) {}
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                height = 54.dp,
                                containerColor = BrandIndigo,
                                depthColor = Color(0xFF3730A3)
                            ) {
                                Text("Grant Notification Access", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                    OnboardingStep.BATTERY_OPTIMIZATION -> {
                        if (hasBatteryPermission) {
                            ThreeDActionButton(
                                onClick = { completeOnboardingOnce() },
                                enabled = !isCompleting,
                                modifier = Modifier.fillMaxWidth(),
                                height = 54.dp,
                                containerColor = BrandEmerald,
                                depthColor = BrandEmeraldDark
                            ) {
                                Text("Open Dashboard", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        } else {
                            ThreeDActionButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                        intent.data = Uri.parse("package:${context.packageName}")
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_SETTINGS))
                                        } catch (_: Exception) {}
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                height = 54.dp,
                                containerColor = BrandEmerald,
                                depthColor = BrandEmeraldDark
                            ) {
                                Text("Allow Background Mode", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            TextButton(
                                onClick = { completeOnboardingOnce() },
                                enabled = !isCompleting
                            ) {
                                Text("Skip for now")
                            }
                        }
                    }
                }
            }
        }
    }
}
