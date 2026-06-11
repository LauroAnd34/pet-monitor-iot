package com.lauro.petguardian

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lauro.petguardian.data.PhotoAutomationPreferences
import com.lauro.petguardian.data.PhotoAlbumStore
import com.lauro.petguardian.data.PetGuardianRepository
import com.lauro.petguardian.ui.UiFormatters

class PetMonitorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return runCatching {
            val payload = PetGuardianRepository.fetchDashboardSync(6)
            val snapshot = payload.snapshot
            PhotoAlbumStore.cleanupOldPhotos(PhotoAutomationPreferences.cleanupDays(applicationContext))
            PetGuardianRepository.syncCloudPhotosSync()
            PhotoAutomationCoordinator.handleMotion(applicationContext, snapshot.motionDetected)
            PhotoAutomationCoordinator.handleTemperature(applicationContext, snapshot.temperatureC)

            if (PhotoAutomationPreferences.notificationsEnabled(applicationContext)) {
                if (!UiFormatters.isRecent(snapshot.createdAt, 180)) {
                    NotificationHelper.notifyAlertIfNew(applicationContext, "Hub offline", "O hub de sensores parou de enviar leituras.")
                }
                if (snapshot.alertText.isNotBlank()) {
                    NotificationHelper.notifyAlertIfNew(applicationContext, "Alerta do pet", snapshot.alertText)
                }
            }
            Result.success()
        }.getOrElse {
            if (PhotoAutomationPreferences.notificationsEnabled(applicationContext)) {
                NotificationHelper.notifyAlertIfNew(applicationContext, "Sistema offline", "Nao foi possivel consultar o hub do pet.")
            }
            Result.retry()
        }
    }
}
