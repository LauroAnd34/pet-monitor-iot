package com.lauro.petguardian.ui

import com.lauro.petguardian.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object UiFormatters {
    fun percent(value: Int?): String = value?.let { "$it%" } ?: "--"

    fun temperature(value: Double?): String =
        value?.let { String.format(Locale("pt", "BR"), "%.1f °C", it) } ?: "--"

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
        val parsed = parseDateOrNull(value) ?: return false
        val diff = (System.currentTimeMillis() - parsed.time) / 1000
        return diff in 0..maxSeconds
    }

    fun date(value: String): String {
        val parsed = parseDateOrNull(value) ?: return value.ifBlank {
            SimpleDateFormat("dd/MM • HH:mm", Locale("pt", "BR")).format(Date())
        }
        return SimpleDateFormat("dd/MM • HH:mm", Locale("pt", "BR")).format(parsed)
    }

    fun dayHeader(value: String): String {
        val parsed = parseDateOrNull(value) ?: return "Sem data"
        val calendar = Calendar.getInstance().apply { time = parsed }
        val now = Calendar.getInstance()
        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            sameDay(calendar, now) -> "Hoje"
            sameDay(calendar, yesterday) -> "Ontem"
            else -> SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR")).format(parsed)
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString() }
        }
    }

    fun relativeTime(value: String): String {
        val parsed = parseDateOrNull(value) ?: return "Horário indisponível"
        val diffSeconds = ((System.currentTimeMillis() - parsed.time) / 1000).coerceAtLeast(0)
        return when {
            diffSeconds < 60 -> "Agora há pouco"
            diffSeconds < 3600 -> "Há ${diffSeconds / 60} min"
            diffSeconds < 86400 -> "Há ${diffSeconds / 3600} h"
            else -> "Há ${diffSeconds / 86400} d"
        }
    }

    fun parseDateOrNull(value: String): Date? {
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

    fun isToday(value: String): Boolean {
        val parsed = parseDateOrNull(value) ?: return false
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { time = parsed }
        return sameDay(now, target)
    }

    fun isWithinLastDays(value: String, days: Int): Boolean {
        val parsed = parseDateOrNull(value) ?: return false
        val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }.timeInMillis
        return parsed.time >= cutoff
    }

    fun isSameDay(first: String, second: String): Boolean {
        val one = parseDateOrNull(first) ?: return false
        val two = parseDateOrNull(second) ?: return false
        return sameDay(Calendar.getInstance().apply { time = one }, Calendar.getInstance().apply { time = two })
    }

    private fun sameDay(first: Calendar, second: Calendar): Boolean {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }
}
