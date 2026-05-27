package com.lauro.petguardian

import android.content.Context
import android.graphics.Color

private const val PREFS_NAME = "pet_guardian_prefs"
private const val PREF_THEME = "theme_id"

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
        ThemePalette("blossom", "Algodão Doce", Color.parseColor("#F29BB2"), Color.parseColor("#DB5F87"), Color.parseColor("#FFFDFD"), Color.parseColor("#FFF2F6"), Color.parseColor("#F3D8E2"), Color.parseColor("#5B3241"), Color.parseColor("#8D6271"), Color.parseColor("#FFD7E4"), Color.parseColor("#FFEAF1")),
        ThemePalette("mint", "Jardim de Hortelã", Color.parseColor("#77D8B7"), Color.parseColor("#2E9C75"), Color.parseColor("#FBFFFD"), Color.parseColor("#EEFDF7"), Color.parseColor("#D1EEDF"), Color.parseColor("#214838"), Color.parseColor("#5F8474"), Color.parseColor("#BDEFD8"), Color.parseColor("#DFFBF0")),
        ThemePalette("butter", "Creme Solar", Color.parseColor("#F2C467"), Color.parseColor("#D69527"), Color.parseColor("#FFFDF8"), Color.parseColor("#FFF7DE"), Color.parseColor("#F0E0AF"), Color.parseColor("#5D4716"), Color.parseColor("#8B7443"), Color.parseColor("#FFE09A"), Color.parseColor("#FFF0C8")),
        ThemePalette("cocoa", "Café com Leite", Color.parseColor("#D8A17E"), Color.parseColor("#A36243"), Color.parseColor("#FFFCFA"), Color.parseColor("#F9F0EA"), Color.parseColor("#E6CBBE"), Color.parseColor("#4B342C"), Color.parseColor("#866559"), Color.parseColor("#F0C3AB"), Color.parseColor("#F8E0D3")),
        ThemePalette("lavender", "Lavanda Noturna", Color.parseColor("#A78FF5"), Color.parseColor("#6F55CA"), Color.parseColor("#FDFCFF"), Color.parseColor("#F3EEFF"), Color.parseColor("#DDD3F7"), Color.parseColor("#41345E"), Color.parseColor("#766A95"), Color.parseColor("#D7CFFF"), Color.parseColor("#ECE5FF")),
        ThemePalette("ocean", "Mar de Vidro", Color.parseColor("#66B8F6"), Color.parseColor("#2A7BC8"), Color.parseColor("#FBFDFF"), Color.parseColor("#EBF6FF"), Color.parseColor("#CDE1F2"), Color.parseColor("#1F486F"), Color.parseColor("#5D7FA0"), Color.parseColor("#CBE7FF"), Color.parseColor("#E4F3FF"))
    )

    fun all(): List<ThemePalette> = palettes

    fun current(context: Context): ThemePalette {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(PREF_THEME, palettes.first().id)
        return palettes.firstOrNull { it.id == id } ?: palettes.first()
    }

    fun save(context: Context, themeId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_THEME, themeId)
            .apply()
    }
}