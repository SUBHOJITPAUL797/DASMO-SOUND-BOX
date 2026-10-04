package com.example

import android.app.Application
import com.example.di.AppModule

class SoundBoxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModule.init(this)
    }
}
