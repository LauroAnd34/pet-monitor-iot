package com.lauro.petguardian

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class PetGuardianApp : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        NotificationHelper.ensureChannel(this)
        val request = PeriodicWorkRequestBuilder<PetMonitorWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "pet_guardian_monitor",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    companion object {
        lateinit var appContext: android.content.Context
            private set
    }
}
