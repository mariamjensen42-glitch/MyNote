package com.cycling.mynote.core.util

import java.util.Locale
import kotlin.math.abs

/**
 * Formats a byte count the way the settings screen's `128 篇 · 24.6 MB` row reads.
 *
 * Uses binary units (1 KB = 1024 B) but the conventional labels, which is what Android itself does
 * for storage figures and therefore what a user comparing this against their file manager expects.
 */
object ByteSizeFormatter {

    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024

    fun format(bytes: Long): String {
        val value = bytes.toDouble()
        return when {
            abs(value) < KB -> "$bytes B"
            abs(value) < MB -> format(value / KB, "KB")
            abs(value) < GB -> format(value / MB, "MB")
            else -> format(value / GB, "GB")
        }
    }

    /** One decimal place, except for whole numbers where the `.0` is noise. */
    private fun format(value: Double, unit: String): String {
        val rounded = Math.round(value * 10) / 10.0
        return if (rounded == rounded.toLong().toDouble()) {
            "${rounded.toLong()} $unit"
        } else {
            String.format(Locale.US, "%.1f %s", rounded, unit)
        }
    }
}
