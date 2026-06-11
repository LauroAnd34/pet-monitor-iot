package com.lauro.petguardian

import android.content.Context
import com.lauro.petguardian.data.PhotoAlbumStore
import com.lauro.petguardian.data.PhotoAutomationPreferences
import com.lauro.petguardian.data.PetGuardianRepository

object PhotoAutomationCoordinator {
    private const val MOTION_COOLDOWN_MS = 5 * 60 * 1000L

    fun handleMotion(context: Context, motionDetected: Boolean): Boolean {
        if (!motionDetected || !PhotoAutomationPreferences.autoCaptureEnabled(context)) return false
        return captureAlert(context, "motion", "Movimento detectado", "A camera registrou um novo momento.")
    }

    fun handleTemperature(context: Context, temperatureC: Double?): Boolean {
        if ((temperatureC ?: 0.0) < 31.0 || !PhotoAutomationPreferences.autoCaptureEnabled(context)) return false
        return captureAlert(context, "temperature", "Temperatura alta", "A camera registrou o ambiente durante o alerta.")
    }

    private fun captureAlert(context: Context, reason: String, title: String, body: String): Boolean {
        if (System.currentTimeMillis() - PhotoAutomationPreferences.lastMotionCapture(context) < MOTION_COOLDOWN_MS) return false

        PhotoAutomationPreferences.markMotionCapture(context)
        val entry = PhotoAlbumStore.requestPhoto("alert")
        return runCatching {
            val captured = PetGuardianRepository.captureSystemPhotoSync(reason)
            PhotoAlbumStore.attachCapture(entry.id, "saved", "Captura automatica durante alerta.", captured.localPath, captured.sourceUrl)
            PhotoAlbumStore.cleanupOldPhotos(PhotoAutomationPreferences.cleanupDays(context))
            if (PhotoAutomationPreferences.notificationsEnabled(context)) {
                NotificationHelper.notifyAlert(context, title, body, captured.localPath)
            }
            true
        }.onFailure {
            PhotoAlbumStore.updateStatus(entry.id, "failed", "Alerta detectado, mas a camera nao respondeu.")
        }.getOrDefault(false)
    }
}
