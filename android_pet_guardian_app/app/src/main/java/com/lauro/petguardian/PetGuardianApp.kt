package com.lauro.petguardian

import android.app.Application

class PetGuardianApp : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }

    companion object {
        lateinit var appContext: android.content.Context
            private set
    }
}
