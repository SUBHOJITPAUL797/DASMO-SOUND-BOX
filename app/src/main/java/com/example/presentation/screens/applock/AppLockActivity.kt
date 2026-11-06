package com.example.presentation.screens.applock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pattern
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
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
import androidx.core.graphics.drawable.toBitmap
import com.example.di.AppModule
import com.example.presentation.components.PatternLockView
import com.example.presentation.components.PatternState
import com.example.presentation.components.PinLockView
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AppLockManager
import com.example.util.IntruderCaptureManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppLockActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: packageName
        AppModule.getOrInit(this)

        // Prevent bypassing via back button: minimize to launcher home
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(homeIntent)
                finish()
            }
        })

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppLockScreen(
                        targetPackage = targetPackage,
                        onUnlocked = {
                            AppLockManager.markAppUnlocked(targetPackage)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppLockScreen(
    targetPackage: String,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = AppModule.settingsRepository
    val settings by (settingsRepo?.getSettings() ?: remember { kotlinx.coroutines.flow.emptyFlow<com.example.domain.model.AppSettings>() })
        .collectAsState(initial = null)

    var currentLockType by remember { mutableStateOf("PATTERN") }
    var lockState by remember { mutableStateOf(PatternState.NORMAL) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf("") }
    var isStatusError by remember { mutableStateOf(false) }

    val appName = remember(targetPackage) { AppLockManager.getAppName(context, targetPackage) }
    val appIcon = remember(targetPackage) { AppLockManager.getAppIcon(context, targetPackage) }

    LaunchedEffect(settings) {
        settings?.let {
            currentLockType = it.appLockType
        }
    }

    fun handleWrongAttempt(attemptType: String) {
        failedAttempts++
        lockState = PatternState.ERROR
        isStatusError = true
        statusMessage = "Incorrect $attemptType. Attempt $failedAttempts logged."

        if (settings?.intruderSelfieEnabled != false) {
            IntruderCaptureManager.captureIntruderPhoto(
                context = context,
                targetPackage = targetPackage,
                targetAppName = appName,
                lockType = attemptType,
                failedAttempts = failedAttempts
            )
        }

        scope.launch {
            delay(1000)
            lockState = PatternState.NORMAL
        }
    }

    fun handleSuccess() {
        lockState = PatternState.SUCCESS
        isStatusError = false
        statusMessage = "Unlocked successfully!"
        scope.launch {
            delay(300)
            onUnlocked()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // App header with target App Icon & Name
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon.toBitmap(160, 160).asImageBitmap(),
                        contentDescription = appName,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Text(
                text = appName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = if (currentLockType == "PATTERN") "Draw Pattern to Unlock" else "Enter PIN to Unlock",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Status message
            AnimatedVisibility(
                visible = statusMessage.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isStatusError) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF22C55E).copy(alpha = 0.15f),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = statusMessage,
                        color = if (isStatusError) Color(0xFFEF4444) else Color(0xFF22C55E),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Lock Input Area (Pattern or PIN)
        Box(
            modifier = Modifier.weight(1f, fill = false),
            contentAlignment = Alignment.Center
        ) {
            if (currentLockType == "PATTERN") {
                PatternLockView(
                    size = 280.dp,
                    state = lockState,
                    enabled = lockState != PatternState.SUCCESS,
                    onPatternStarted = {
                        if (lockState == PatternState.ERROR) {
                            lockState = PatternState.NORMAL
                            statusMessage = ""
                        }
                    },
                    onPatternCompleted = { drawnPattern ->
                        val savedPattern = settings?.appLockPattern
                        if (savedPattern.isNullOrBlank() || drawnPattern == savedPattern) {
                            handleSuccess()
                        } else {
                            handleWrongAttempt("PATTERN")
                        }
                    },
                    onPatternTooShort = {
                        statusMessage = "Connect at least 4 dots"
                        isStatusError = true
                    }
                )
            } else {
                PinLockView(
                    pinLength = 4,
                    state = lockState,
                    onPinEntered = { enteredPin ->
                        val savedPin = settings?.appLockPin
                        if (savedPin.isNullOrBlank() || enteredPin == savedPin) {
                            handleSuccess()
                        } else {
                            handleWrongAttempt("PIN")
                        }
                    }
                )
            }
        }

        // Bottom option to switch between Pattern and PIN
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val hasBothSet = !settings?.appLockPattern.isNullOrBlank() && !settings?.appLockPin.isNullOrBlank()
            if (hasBothSet) {
                TextButton(
                    onClick = {
                        currentLockType = if (currentLockType == "PATTERN") "PIN" else "PATTERN"
                        lockState = PatternState.NORMAL
                        statusMessage = ""
                    }
                ) {
                    Icon(
                        imageVector = if (currentLockType == "PATTERN") Icons.Default.Pin else Icons.Default.Pattern,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (currentLockType == "PATTERN") "Use PIN instead" else "Use Pattern instead",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Protected by DASMO Intruder Shield",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

