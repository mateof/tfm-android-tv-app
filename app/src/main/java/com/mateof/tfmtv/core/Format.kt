package com.mateof.tfmtv.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {

    fun bytes(value: Long?): String {
        if (value == null || value < 0) return ""
        if (value < 1024) return "$value B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = value.toDouble() / 1024
        var i = 0
        while (v >= 1024 && i < units.size - 1) {
            v /= 1024; i++
        }
        return String.format(Locale.getDefault(), if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun date(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return runCatching {
            Instant.parse(iso).atZone(ZoneId.systemDefault()).format(dateFormatter)
        }.getOrElse {
            runCatching {
                java.time.LocalDateTime.parse(iso.substringBefore('.').substringBefore('+'))
                    .format(dateFormatter)
            }.getOrDefault(iso)
        }
    }

    fun duration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}
