package com.lauro.petguardian.ui

import com.lauro.petguardian.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object UiFormatters {
    fun percent(value: Int?): String = value?.let { "${it}%" } ?: "--"

    fun temperature(value: Double?): String =
        value?.let { String.format(Locale("pt", "BR"), "%.1f \u00B0C", it) } ?: "--"

    fun humidity(value: Double?): String =
        value?.let { String.format(Locale("pt", "BR"), "%.0f%%", it) } ?: "--"

    fun yesNo(value: Boolean, yes: String, no: String): String = if (value) yes else no

    fun value(value: Int?): String = value?.toString() ?: "--"

    fun lux(value: Int?): String = value?.let { "$it lux" } ?: "--"

    fun lightProgress(raw: Int?): Int = (((raw ?: 0).coerceIn(0, 4095) / 4095.0) * 100).toInt()

    fun lightLabel(progress: Int): Int = when {
        progress < 28 -> R.string.light_label_low
        progress < 68 -> R.string.light_label_medium
        else -> R.string.light_label_high
    }

    fun isRecent(value: String, maxSeconds: Long = 90): Boolean {
        val parsed = parseDate(value) ?: return false
        val diff = (System.currentTimeMillis() - parsed.time) / 1000
        return diff in 0..maxSeconds
    }

    fun date(value: String): String {
        val parsed = parseDate(value) ?: return value.ifBlank {
            SimpleDateFormat("dd/MM \u2022 HH:mm", Locale("pt", "BR")).format(Date())
        }
        return SimpleDateFormat("dd/MM \u2022 HH:mm", Locale("pt", "BR")).format(parsed)
    }

    fun relativeTime(value: String): String {
        val parsed = parseDate(value) ?: return "Horário indisponível"
        val diffSeconds = ((System.currentTimeMillis() - parsed.time) / 1000).coerceAtLeast(0)
        return when {
            diffSeconds < 60 -> "Agora há pouco"
            diffSeconds < 3600 -> "Há ${diffSeconds / 60} min"
            diffSeconds < 86400 -> "Há ${diffSeconds / 3600} h"
            else -> "Há ${diffSeconds / 86400} d"
        }
    }

    private fun parseDate(value: String): Date? {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX"
        )
        for (pattern in formats) {
            val parsed = runCatching { SimpleDateFormat(pattern, Locale.US).parse(value) }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }
}