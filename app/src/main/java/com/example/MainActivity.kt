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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (settingsRepo != null) {
                        val settings by settingsRepo.getSettings().collectAsState(initial = null)
                        val currentSettings = settings
                        
                        if (currentSettings != null) {
                            LaunchedEffect(currentSettings.isEnabled) {
                                if (currentSettings.isEnabled && !SoundBoxForegroundService.isRunning) {
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
            if (settings.appLockEnabled && settings.lockedAppPackages.contains(packageName)) {
                if (!AppLockManager.isAppTemporarilyUnlocked(packageName)) {
                    val intent = Intent(this@MainActivity, AppLockActivity::class.java).apply {
                        putExtra(AppLockActivity.EXTRA_PACKAGE_NAME, packageName)
                    }
                    startActivity(intent)
                }
            }
        }
    }
}
