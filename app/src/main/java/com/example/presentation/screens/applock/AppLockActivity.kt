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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import com.example.di.AppModule
import com.example.presentation.components.PasswordLockView
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

        fun authenticatePattern(drawnPattern: String, savedPattern: String?): Boolean {
            if (savedPattern.isNullOrBlank()) return false
            return drawnPattern == savedPattern
        }

        fun authenticatePin(enteredPin: String, savedPin: String?): Boolean {
            if (savedPin.isNullOrBlank()) return false
            return enteredPin == savedPin
        }

        fun authenticatePassword(enteredPassword: String, savedPassword: String?): Boolean {
            if (savedPassword.isNullOrBlank()) return false
            return enteredPassword == savedPassword
        }
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
                try {
                    startActivity(homeIntent)
                } catch (_: Exception) {}
                finish()
            }
        })

        setContent {
            val themeSettings by (AppModule.settingsRepository?.getSettings()
                ?: remember { kotlinx.coroutines.flow.emptyFlow<com.example.domain.model.AppSettings>() })
                .collectAsState(initial = null)
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (themeSettings?.theme ?: "LIGHT") {
                "DARK" -> true
                "LIGHT" -> false
                else -> systemDark
            }
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = this@AppLockActivity.window
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !useDarkTheme
                        isAppearanceLightNavigationBars = !useDarkTheme
                    }
                }
            }
            MyApplicationTheme(darkTheme = useDarkTheme) {
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

enum class AppLockMethod(val serializedKey: String, val switchLabel: String, val promptLabel: String) {
    PATTERN("PATTERN", "Use Pattern", "Draw Pattern to Unlock"),
    PIN("PIN", "Use PIN", "Enter PIN to Unlock"),
    PASSWORD("PASSWORD", "Use Password", "Enter Password to Unlock");

    companion object {
        fun fromKey(key: String?): AppLockMethod = when (key) {
            PIN.serializedKey -> PIN
            PASSWORD.serializedKey -> PASSWORD
            else -> PATTERN
        }

        fun configuredMethods(settings: com.example.domain.model.AppSettings): List<AppLockMethod> = buildList {
            if (settings.appLockPattern.isNotBlank()) add(PATTERN)
            if (settings.appLockPin.isNotBlank()) add(PIN)
            if (settings.appLockPassword.isNotBlank()) add(PASSWORD)
        }

        fun resolvePreferredMethod(settings: com.example.domain.model.AppSettings): AppLockMethod {
            val preferred = fromKey(settings.appLockType)
            val configured = configuredMethods(settings)
            return when {
                configured.contains(preferred) -> preferred
                configured.isNotEmpty() -> configured.first()
                else -> preferred
            }
        }
    }
}

private sealed interface LockChallengePhase {
    data object Ready : LockChallengePhase
    data class Error(val message: String, val attemptSeq: Int) : LockChallengePhase
    data object Success : LockChallengePhase
}

@Composable
fun AppLockScreen(
    targetPackage: String,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val settingsRepo = AppModule.settingsRepository
    val settings by (settingsRepo?.getSettings() ?: remember { kotlinx.coroutines.flow.emptyFlow<com.example.domain.model.AppSettings>() })
        .collectAsState(initial = null)

    var activeMethod by remember { mutableStateOf(AppLockMethod.PATTERN) }
    var methodManuallySelected by remember { mutableStateOf(false) }
    var challengePhase by remember { mutableStateOf<LockChallengePhase>(LockChallengePhase.Ready) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    var attemptSeq by remember { mutableIntStateOf(0) }

    val lockState = when (challengePhase) {
        LockChallengePhase.Ready -> PatternState.NORMAL
        is LockChallengePhase.Error -> PatternState.ERROR
        LockChallengePhase.Success -> PatternState.SUCCESS
    }
    val statusMessage = when (val phase = challengePhase) {
        LockChallengePhase.Ready -> ""
        is LockChallengePhase.Error -> phase.message
        LockChallengePhase.Success -> "Unlocked successfully!"
    }
    val isStatusError = challengePhase is LockChallengePhase.Error

    val appName = remember(targetPackage) { AppLockManager.getAppName(context, targetPackage) }
    val appIcon = remember(targetPackage) { AppLockManager.getAppIcon(context, targetPackage) }

    LaunchedEffect(settings) {
        settings?.let { currentSettings ->
            if (!AppLockManager.hasConfiguredCredentials(currentSettings)) {
                // Fail open if credentials are empty so user is never permanently locked out
                settingsRepo?.updateAppLockEnabled(false)
                onUnlocked()
                return@let
            }
            if (!methodManuallySelected || !AppLockMethod.configuredMethods(currentSettings).contains(activeMethod)) {
                activeMethod = AppLockMethod.resolvePreferredMethod(currentSettings)
            }
        }
    }

    // State-driven auto-reset / completion timers (auto-cancelled if phase changes!)
    LaunchedEffect(challengePhase) {
        when (challengePhase) {
            is LockChallengePhase.Error -> {
                delay(1000)
                challengePhase = LockChallengePhase.Ready
            }
            LockChallengePhase.Success -> {
                delay(300)
                onUnlocked()
            }
            LockChallengePhase.Ready -> Unit
        }
    }

    fun handleWrongAttempt(method: AppLockMethod) {
        if (challengePhase == LockChallengePhase.Success) return
        failedAttempts++
        attemptSeq++
        challengePhase = LockChallengePhase.Error(
            message = "Incorrect ${method.serializedKey}. Attempt $failedAttempts logged.",
            attemptSeq = attemptSeq
        )

        if (settings?.intruderSelfieEnabled != false) {
            IntruderCaptureManager.captureIntruderPhoto(
                context = context,
                targetPackage = targetPackage,
                targetAppName = appName,
                lockType = method.serializedKey,
                failedAttempts = failedAttempts
            )
        }
    }

    fun handleSuccess() {
        if (challengePhase == LockChallengePhase.Success) return
        challengePhase = LockChallengePhase.Success
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
                text = activeMethod.promptLabel,
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

        // Lock Input Area (Pattern, PIN, or Password)
        Box(
            modifier = Modifier.weight(1f, fill = false),
            contentAlignment = Alignment.Center
        ) {
            when (activeMethod) {
                AppLockMethod.PATTERN -> {
                    PatternLockView(
                        size = 280.dp,
                        state = lockState,
                        enabled = (settings != null) && challengePhase != LockChallengePhase.Success,
                        onPatternStarted = {
                            if (challengePhase is LockChallengePhase.Error) {
                                challengePhase = LockChallengePhase.Ready
                            }
                        },
                        onPatternCompleted = { drawnPattern ->
                            val currentSettings = settings
                            if (currentSettings == null) {
                                attemptSeq++
                                challengePhase = LockChallengePhase.Error(
                                    "Loading security settings, please wait...",
                                    attemptSeq
                                )
                                return@PatternLockView
                            }
                            if (AppLockActivity.authenticatePattern(drawnPattern, currentSettings.appLockPattern)) {
                                handleSuccess()
                            } else {
                                handleWrongAttempt(AppLockMethod.PATTERN)
                            }
                        },
                        onPatternTooShort = {
                            attemptSeq++
                            challengePhase = LockChallengePhase.Error("Connect at least 4 dots", attemptSeq)
                        }
                    )
                }
                AppLockMethod.PIN -> {
                    PinLockView(
                        pinLength = 4,
                        state = lockState,
                        onPinEntered = { enteredPin ->
                            val currentSettings = settings
                            if (currentSettings == null) {
                                attemptSeq++
                                challengePhase = LockChallengePhase.Error(
                                    "Loading security settings, please wait...",
                                    attemptSeq
                                )
                                return@PinLockView
                            }
                            if (AppLockActivity.authenticatePin(enteredPin, currentSettings.appLockPin)) {
                                handleSuccess()
                            } else {
                                handleWrongAttempt(AppLockMethod.PIN)
                            }
                        }
                    )
                }
                AppLockMethod.PASSWORD -> {
                    PasswordLockView(
                        state = lockState,
                        enabled = (settings != null) && challengePhase != LockChallengePhase.Success,
                        onPasswordEntered = { enteredPassword ->
                            val currentSettings = settings
                            if (currentSettings == null) {
                                attemptSeq++
                                challengePhase = LockChallengePhase.Error(
                                    "Loading security settings, please wait...",
                                    attemptSeq
                                )
                                return@PasswordLockView
                            }
                            if (AppLockActivity.authenticatePassword(enteredPassword, currentSettings.appLockPassword)) {
                                handleSuccess()
                            } else {
                                handleWrongAttempt(AppLockMethod.PASSWORD)
                            }
                        }
                    )
                }
            }
        }

        // Bottom option to switch between configured lock types
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val configuredTypes = remember(settings) {
                settings?.let { AppLockMethod.configuredMethods(it) } ?: emptyList()
            }

            val otherTypes = configuredTypes.filter { it != activeMethod }

            if (otherTypes.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    otherTypes.forEach { method ->
                        TextButton(
                            onClick = {
                                if (challengePhase != LockChallengePhase.Success) {
                                    methodManuallySelected = true
                                    activeMethod = method
                                    challengePhase = LockChallengePhase.Ready
                                }
                            }
                        ) {
                            Icon(
                                imageVector = when (method) {
                                    AppLockMethod.PATTERN -> Icons.Default.Pattern
                                    AppLockMethod.PIN -> Icons.Default.Pin
                                    AppLockMethod.PASSWORD -> Icons.Default.Password
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = method.switchLabel,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
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

