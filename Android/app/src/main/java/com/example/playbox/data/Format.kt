package com.example.playbox.data

import java.util.Locale
import java.util.concurrent.TimeUnit

object Format {

    /** 0:07, 4:32, 1:12:05 — the badge format. */
    fun duration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun compact(count: Int): String = when {
        count < 1_000 -> count.toString()
        count < 1_000_000 -> {
            val v = count / 1_000f
            if (v < 10) String.format(Locale.US, "%.1fK", v) else String.format(Locale.US, "%.0fK", v)
        }
        else -> {
            val v = count / 1_000_000f
            if (v < 10) String.format(Locale.US, "%.1fM", v) else String.format(Locale.US, "%.0fM", v)
        }
    }

    fun plays(count: Int): String = "${compact(count)} ${if (count == 1) "view" else "views"}"

    fun fileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "${bytes} B" else String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    fun age(epochMs: Long): String {
        if (epochMs <= 0) return "unknown"
        val delta = System.currentTimeMillis() - epochMs
        if (delta < 0) return "just now"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
        val hours = TimeUnit.MILLISECONDS.toHours(delta)
        val days = TimeUnit.MILLISECONDS.toDays(delta)
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes ${plural(minutes, "minute")} ago"
            hours < 24 -> "$hours ${plural(hours, "hour")} ago"
            days < 7 -> "$days ${plural(days, "day")} ago"
            days < 30 -> "${days / 7} ${plural(days / 7, "week")} ago"
            days < 365 -> "${days / 30} ${plural(days / 30, "month")} ago"
            else -> "${days / 365} ${plural(days / 365, "year")} ago"
        }
    }

    private fun plural(n: Long, word: String) = if (n == 1L) word else "${word}s"

    /** "1.2K views · 3 days ago" */
    fun subtitle(video: Video): String = "${plays(video.plays)} · ${age(video.dateAddedMs)}"
}
