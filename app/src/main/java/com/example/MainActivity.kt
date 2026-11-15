package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.di.AppModule
import com.example.presentation.navigation.AppNavGraph
import com.example.presentation.screens.applock.AppLockActivity
import com.example.presentation.screens.onboarding.OnboardingScreen
import com.example.service.SoundBoxForegroundService
import com.example.ui.theme.MyApplicationTheme
import com.example.util.AppLockManager
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        
        AppModule.getOrInit(this)
        val settingsRepo = AppModule.settingsRepository
        
        setContent {
            val settingsState = if (settingsRepo != null) {
                settingsRepo.getSettings().collectAsState(initial = null)
            } else {
                androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(null) }
            }
            val currentSettings = settingsState.value
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val useDarkTheme = when (currentSettings?.theme ?: "LIGHT") {
                "DARK" -> true
                "LIGHT" -> false
                else -> systemDark
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = this@MainActivity.window
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
                    if (currentSettings != null) {
                        LaunchedEffect(currentSettings.isEnabled, currentSettings.appLockEnabled) {
                            if ((currentSettings.isEnabled || currentSettings.appLockEnabled) && !SoundBoxForegroundService.isRunning) {
                                SoundBoxForegroundService.start(this@MainActivity)
                            }
                        }

                        if (currentSettings.hasCompletedOnboarding) {
                            AppNavGraph()
                        } else {
                            OnboardingScreen(
                                onComplete = {
                                    SoundBoxForegroundService.start(this@MainActivity)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.example.service.PaymentNotificationListener.rebindIfDisconnected(this)
        checkAppLock()
    }

    private fun checkAppLock() {
        val settingsRepo = AppModule.settingsRepository ?: return
        lifecycleScope.launch {
            val settings = settingsRepo.getSettings().firstOrNull() ?: return@launch
            val hasCredentials = settings.appLockPattern.isNotBlank() || settings.appLockPin.isNotBlank()
            if (settings.appLockEnabled && hasCredentials && settings.lockedAppPackages.contains(packageName)) {
                if (!AppLockManager.isAppTemporarilyUnlocked(packageName)) {
                    try {
                        val intent = Intent(this@MainActivity, AppLockActivity::class.java).apply {
                            putExtra(AppLockActivity.EXTRA_PACKAGE_NAME, packageName)
                        }
                        startActivity(intent)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}
