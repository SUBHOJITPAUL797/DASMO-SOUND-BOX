package com.example

import android.app.Application
import com.example.di.AppModule
import com.example.util.ChimePlayer

class SoundBoxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModule.init(this)
        ChimePlayer.init(this)
    }
}
