package com.lauro.petguardian

import android.content.Context

private const val PROFILE_PREFS = "pet_guardian_profile"
private const val PREF_CUSTOM_PET_NAME = "custom_pet_name"
private const val PREF_CUSTOM_HOME_NAME = "custom_home_name"
private const val PREF_ONBOARDING_SEEN = "onboarding_seen"

object ProfileManager {
    private fun prefs(context: Context) = context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)

    fun petName(context: Context): String? = prefs(context).getString(PREF_CUSTOM_PET_NAME, null)?.takeIf { it.isNotBlank() }
    fun homeName(context: Context): String? = prefs(context).getString(PREF_CUSTOM_HOME_NAME, null)?.takeIf { it.isNotBlank() }

    fun savePetName(context: Context, value: String) {
        prefs(context).edit().putString(PREF_CUSTOM_PET_NAME, value.trim()).apply()
    }

    fun saveHomeName(context: Context, value: String) {
        prefs(context).edit().putString(PREF_CUSTOM_HOME_NAME, value.trim()).apply()
    }

    fun onboardingSeen(context: Context): Boolean = prefs(context).getBoolean(PREF_ONBOARDING_SEEN, false)
    fun saveOnboardingSeen(context: Context) { prefs(context).edit().putBoolean(PREF_ONBOARDING_SEEN, true).apply() }
}
