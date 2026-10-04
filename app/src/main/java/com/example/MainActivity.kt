package com.example

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
import com.example.di.AppModule
import com.example.presentation.navigation.AppNavGraph
import com.example.presentation.screens.onboarding.OnboardingScreen
import com.example.service.SoundBoxForegroundService
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        
        AppModule.getOrInit(this)
        val settingsRepo = AppModule.settingsRepository!!
        
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val settings by settingsRepo.getSettings().collectAsState(initial = null)
                    
                    if (settings != null) {
                        LaunchedEffect(settings!!.isEnabled) {
                            if (settings!!.isEnabled && !SoundBoxForegroundService.isRunning) {
                                SoundBoxForegroundService.start(this@MainActivity)
                            }
                        }

                        if (settings!!.hasCompletedOnboarding) {
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
