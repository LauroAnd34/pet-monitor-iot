package com.lauro.petguardian

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {
    private const val CHANNEL_ID = "pet_guardian_alerts"
    private const val CHANNEL_NAME = "Alertas do pet"
    private const val PREFS = "pet_guardian_notifications"
    private const val KEY_LAST_ALERT = "last_alert"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = "Alertas do ambiente e do pet"
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun notifyAlertIfNew(context: Context, title: String, body: String) {
        if (body.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_ALERT, null) == body) return
        notifyAlert(context, title, body)
        prefs.edit().putString(KEY_LAST_ALERT, body).apply()
    }

    fun notifyAlert(context: Context, title: String, body: String) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_home)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(body.hashCode(), notification) }
    }
}