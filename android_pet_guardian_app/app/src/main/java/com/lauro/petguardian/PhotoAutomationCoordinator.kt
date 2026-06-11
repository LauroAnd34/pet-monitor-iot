package com.lauro.petguardian

import android.content.Context
import com.lauro.petguardian.data.PhotoAlbumStore
import com.lauro.petguardian.data.PhotoAutomationPreferences

object PhotoAutomationCoordinator {
    private const val MOTION_COOLDOWN_MS = 5 * 60 * 1000L

    fun handleMotion(context: Context, motionDetected: Boolean): Boolean {
        if (!motionDetected || !PhotoAutomationPreferences.autoCaptureEnabled(context)) return false
        return captureAlert(context, "Movimento detectado", "A camera registrou um novo momento.")
    }

    fun handleTemperature(context: Context, temperatureC: Double?): Boolean {
        if ((temperatureC ?: 0.0) < 31.0 || !PhotoAutomationPreferences.autoCaptureEnabled(context)) return false
        return captureAlert(context, "Temperatura alta", "A camera registrou o ambiente durante o alerta.")
    }

    private fun captureAlert(context: Context, title: String, body: String): Boolean {
        if (System.currentTimeMillis() - PhotoAutomationPreferences.lastMotionCapture(context) < MOTION_COOLDOWN_MS) return false

        PhotoAutomationPreferences.markMotionCapture(context)
        val latestPhoto = PhotoAlbumStore.all().firstOrNull { it.imagePath.isNotBlank() }
        if (PhotoAutomationPreferences.notificationsEnabled(context)) {
            NotificationHelper.notifyAlert(context, title, body, latestPhoto?.imagePath)
        }
        return true
    }
}
