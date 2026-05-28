package com.lauro.petguardian

import android.content.Context
import android.graphics.Color

private const val PREFS_NAME = "pet_guardian_prefs"
private const val PREF_THEME = "theme_id"
private const val PREF_AVATAR_PATH = "avatar_path"
private const val PREF_ANIMATIONS = "animations_enabled"
private const val PREF_AUTO_REFRESH = "auto_refresh_enabled"
private const val PREF_RELATIVE_TIME = "relative_time_enabled"
private const val PREF_TEXT_SCALE = "text_scale"
private const val PREF_ANIMATION_INTENSITY = "animation_intensity"
private const val PREF_SHOW_GAS = "show_gas"
private const val PREF_SHOW_SYNC = "show_sync"
private const val PREF_START_TAB = "start_tab"

data class ThemePalette(
    val id: String,
    val name: String,
    val primary: Int,
    val primaryDark: Int,
    val surface: Int,
    val background: Int,
    val border: Int,
    val text: Int,
    val softText: Int,
    val accent: Int,
    val chip: Int
)

object ThemeManager {
    private val palettes = listOf(
        ThemePalette("blossom", "Algodao Doce", Color.parseColor("#F58FA9"), Color.parseColor("#D64F7A"), Color.parseColor("#FFFDFE"), Color.parseColor("#FFF1F6"), Color.parseColor("#F3D2DE"), Color.parseColor("#573241"), Color.parseColor("#8F6776"), Color.parseColor("#FFD6E3"), Color.parseColor("#FFE9F1")),
        ThemePalette("mint", "Jardim de Hortela", Color.parseColor("#5DCAA7"), Color.parseColor("#1F8A66"), Color.parseColor("#FAFFFC"), Color.parseColor("#ECFBF4"), Color.parseColor("#CDEBDD"), Color.parseColor("#214236"), Color.parseColor("#5B8072"), Color.parseColor("#C6F3E3"), Color.parseColor("#E4FBF2")),
        ThemePalette("butter", "Creme Solar", Color.parseColor("#F1BE57"), Color.parseColor("#BE7A14"), Color.parseColor("#FFFDF7"), Color.parseColor("#FFF7DA"), Color.parseColor("#EEDDAA"), Color.parseColor("#5D4512"), Color.parseColor("#8F7840"), Color.parseColor("#FFE39D"), Color.parseColor("#FFF2C9")),
        ThemePalette("cocoa", "Cafe com Leite", Color.parseColor("#C88F6C"), Color.parseColor("#8C5337"), Color.parseColor("#FFFDFC"), Color.parseColor("#F8EFEA"), Color.parseColor("#E7C7B9"), Color.parseColor("#442F29"), Color.parseColor("#7B6259"), Color.parseColor("#F0C2A8"), Color.parseColor("#F9E2D7")),
        ThemePalette("lavender", "Lavanda Noturna", Color.parseColor("#9E84F8"), Color.parseColor("#5840B0"), Color.parseColor("#FDFBFF"), Color.parseColor("#F2EEFF"), Color.parseColor("#D8CEF6"), Color.parseColor("#3C315B"), Color.parseColor("#72668E"), Color.parseColor("#DACEFF"), Color.parseColor("#EEE8FF")),
        ThemePalette("ocean", "Mar de Vidro", Color.parseColor("#5CAFE9"), Color.parseColor("#175E9B"), Color.parseColor("#FBFDFF"), Color.parseColor("#EAF5FF"), Color.parseColor("#C6DCEE"), Color.parseColor("#1D4263"), Color.parseColor("#5E7D98"), Color.parseColor("#CAE8FF"), Color.parseColor("#E6F4FF"))
    )

    fun all(): List<ThemePalette> = palettes

    fun current(context: Context): ThemePalette {
        val id = prefs(context).getString(PREF_THEME, palettes.first().id)
        return palettes.firstOrNull { it.id == id } ?: palettes.first()
    }

    fun save(context: Context, themeId: String) {
        prefs(context).edit().putString(PREF_THEME, themeId).apply()
    }

    fun avatarPath(context: Context): String? = prefs(context).getString(PREF_AVATAR_PATH, null)

    fun saveAvatarPath(context: Context, path: String) {
        prefs(context).edit().putString(PREF_AVATAR_PATH, path).commit()
    }

    fun clearAvatar(context: Context) {
        prefs(context).edit().remove(PREF_AVATAR_PATH).commit()
    }

    fun animationsEnabled(context: Context): Boolean = prefs(context).getBoolean(PREF_ANIMATIONS, true)

    fun saveAnimationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_ANIMATIONS, enabled).apply()
    }

    fun autoRefreshEnabled(context: Context): Boolean = prefs(context).getBoolean(PREF_AUTO_REFRESH, true)

    fun saveAutoRefreshEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_AUTO_REFRESH, enabled).apply()
    }

    fun relativeTimeEnabled(context: Context): Boolean = prefs(context).getBoolean(PREF_RELATIVE_TIME, true)

    fun saveRelativeTimeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_RELATIVE_TIME, enabled).apply()
    }

    fun textScale(context: Context): Float = when (prefs(context).getString(PREF_TEXT_SCALE, "standard")) {
        "large" -> 1.12f
        else -> 1f
    }

    fun textScaleId(context: Context): String = prefs(context).getString(PREF_TEXT_SCALE, "standard") ?: "standard"

    fun saveTextScale(context: Context, value: String) {
        prefs(context).edit().putString(PREF_TEXT_SCALE, value).apply()
    }

    fun animationIntensity(context: Context): Float = when (prefs(context).getString(PREF_ANIMATION_INTENSITY, "normal")) {
        "soft" -> 0.75f
        "vivid" -> 1.25f
        else -> 1f
    }

    fun animationIntensityId(context: Context): String = prefs(context).getString(PREF_ANIMATION_INTENSITY, "normal") ?: "normal"

    fun saveAnimationIntensity(context: Context, value: String) {
        prefs(context).edit().putString(PREF_ANIMATION_INTENSITY, value).apply()
    }

    fun showGasCard(context: Context): Boolean = prefs(context).getBoolean(PREF_SHOW_GAS, true)

    fun saveShowGasCard(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_SHOW_GAS, enabled).apply()
    }

    fun showSyncCard(context: Context): Boolean = prefs(context).getBoolean(PREF_SHOW_SYNC, true)

    fun saveShowSyncCard(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(PREF_SHOW_SYNC, enabled).apply()
    }

    fun startTab(context: Context): String = prefs(context).getString(PREF_START_TAB, "home") ?: "home"

    fun saveStartTab(context: Context, tabId: String) {
        prefs(context).edit().putString(PREF_START_TAB, tabId).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
