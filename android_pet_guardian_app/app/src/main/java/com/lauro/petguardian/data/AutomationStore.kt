package com.lauro.petguardian.data

import android.content.Context
import org.json.JSONObject

object AutomationStore {
    private const val PREFS = "pet_guardian_automation"
    private const val KEY_LIGHT_MINUTES = "light_minutes"
    private const val KEY_PUMP_SECONDS = "pump_seconds"
    private const val KEY_FEED_HOURS = "feed_hours"
    private const val KEY_DARK_THRESHOLD = "dark_threshold"
    private const val KEY_AUTO_LIGHT = "auto_light"
    private const val KEY_AUTO_FEED = "auto_feed"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun snapshot(context: Context): JSONObject = JSONObject()
        .put("lightMinutes", prefs(context).getInt(KEY_LIGHT_MINUTES, 5))
        .put("pumpSeconds", prefs(context).getInt(KEY_PUMP_SECONDS, 10))
        .put("feedHours", prefs(context).getInt(KEY_FEED_HOURS, 6))
        .put("darkThreshold", prefs(context).getInt(KEY_DARK_THRESHOLD, 1400))
        .put("autoLight", prefs(context).getBoolean(KEY_AUTO_LIGHT, true))
        .put("autoFeed", prefs(context).getBoolean(KEY_AUTO_FEED, true))

    fun save(
        context: Context,
        lightMinutes: Int,
        pumpSeconds: Int,
        feedHours: Int,
        darkThreshold: Int,
        autoLight: Boolean,
        autoFeed: Boolean
    ) {
        prefs(context).edit()
            .putInt(KEY_LIGHT_MINUTES, lightMinutes)
            .putInt(KEY_PUMP_SECONDS, pumpSeconds)
            .putInt(KEY_FEED_HOURS, feedHours)
            .putInt(KEY_DARK_THRESHOLD, darkThreshold)
            .putBoolean(KEY_AUTO_LIGHT, autoLight)
            .putBoolean(KEY_AUTO_FEED, autoFeed)
            .apply()
    }
}
