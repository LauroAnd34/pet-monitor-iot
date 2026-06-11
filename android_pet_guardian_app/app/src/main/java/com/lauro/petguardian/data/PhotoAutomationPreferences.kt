package com.lauro.petguardian.data

import android.content.Context

object PhotoAutomationPreferences {
    private const val PREFS = "photo_automation"
    private const val AUTO_CAPTURE = "auto_capture"
    private const val NOTIFICATIONS = "notifications"
    private const val CLEANUP_DAYS = "cleanup_days"
    private const val LAST_MOTION_CAPTURE = "last_motion_capture"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun autoCaptureEnabled(context: Context) = prefs(context).getBoolean(AUTO_CAPTURE, true)
    fun notificationsEnabled(context: Context) = prefs(context).getBoolean(NOTIFICATIONS, true)
    fun cleanupDays(context: Context) = prefs(context).getInt(CLEANUP_DAYS, 30)
    fun lastMotionCapture(context: Context) = prefs(context).getLong(LAST_MOTION_CAPTURE, 0L)

    fun setAutoCapture(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(AUTO_CAPTURE, enabled).apply()
    fun setNotifications(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean(NOTIFICATIONS, enabled).apply()
    fun setCleanupDays(context: Context, days: Int) = prefs(context).edit().putInt(CLEANUP_DAYS, days).apply()
    fun markMotionCapture(context: Context) = prefs(context).edit().putLong(LAST_MOTION_CAPTURE, System.currentTimeMillis()).apply()
}
