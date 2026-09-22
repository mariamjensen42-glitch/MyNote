package com.cycling.mynote.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Timestamp formatting for the three relative-time styles the design uses.
 *
 * Every function takes `now` so the behaviour is testable without freezing the clock. All of them
 * resolve calendar days in [zone] rather than by subtracting 24-hour blocks, so "昨天" means
 * yesterday on the user's calendar and not "between 24 and 48 hours ago".
 */
class RelativeTimeFormatter(
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.SIMPLIFIED_CHINESE,
) {
    private val weekdayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val monthDayFormat = DateTimeFormatter.ofPattern("M月d日", locale)
    private val fullDateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日", locale)

    /**
     * Style used by the note rows in the library: `12 分钟前`, `今天 09:24`, `昨天 21:07`,
     * `周二 18:02`.
     */
    fun forNoteRow(millis: Long, now: Long = System.currentTimeMillis()): String {
        val minutes = minutesBetween(millis, now)
        if (minutes < 1) return "刚刚"
        if (minutes < 60) return "$minutes 分钟前"

        val date = localDate(millis)
        val today = localDate(now)
        val time = localTime(millis)
        val daysApart = daysBetween(date, today)
        return when {
            daysApart == 0L -> "今天 $time"
            daysApart == 1L -> "昨天 $time"
            daysApart in 2L..6L -> "${weekdayNames[date.dayOfWeek.value - 1]} $time"
            date.year == today.year -> monthDayFormat.format(date)
            else -> fullDateFormat.format(date)
        }
    }

    /**
     * Style used by search results: `2 天前`, `上周`, `4 天前`. Coarser than [forNoteRow] because a
     * result list mixes notes from unrelated parts of the repository.
     */
    fun forSearchResult(millis: Long, now: Long = System.currentTimeMillis()): String {
        val minutes = minutesBetween(millis, now)
        if (minutes < 1) return "刚刚"
        if (minutes < 60) return "$minutes 分钟前"

        val hours = minutes / 60
        if (hours < 24) return "$hours 小时前"

        val daysApart = daysBetween(localDate(millis), localDate(now))
        return when {
            daysApart < 7 -> "$daysApart 天前"
            daysApart < 14 -> "上周"
            daysApart < 30 -> "${daysApart / 7} 周前"
            else -> fullDateFormat.format(localDate(millis))
        }
    }

    /**
     * Style used by the folder tree, which has the narrowest column: `今天`, `昨天`, `周二`,
     * `3 天前`.
     */
    fun forTreeRow(millis: Long, now: Long = System.currentTimeMillis()): String {
        val minutes = minutesBetween(millis, now)
        if (minutes < 1) return "刚刚"
        if (minutes < 60) return "$minutes 分钟前"

        val date = localDate(millis)
        val daysApart = daysBetween(date, localDate(now))
        return when {
            daysApart == 0L -> "今天"
            daysApart == 1L -> "昨天"
            daysApart in 2L..6L -> weekdayNames[date.dayOfWeek.value - 1]
            else -> "$daysApart 天前"
        }
    }

    /** Absolute date shown on the about/settings rows. */
    fun absoluteDate(millis: Long): String = fullDateFormat.format(localDate(millis))

    /** Timestamp shown next to the editor's save chip. */
    fun forSaveStamp(millis: Long, now: Long = System.currentTimeMillis()): String {
        val minutes = minutesBetween(millis, now)
        return when {
            minutes < 1 -> "刚刚保存"
            minutes < 60 -> "$minutes 分钟前保存"
            else -> "${forNoteRow(millis, now)} 保存"
        }
    }

    private fun minutesBetween(from: Long, to: Long): Long = (to - from).coerceAtLeast(0) / 60_000

    private fun daysBetween(from: LocalDate, to: LocalDate): Long =
        to.toEpochDay() - from.toEpochDay()

    private fun localDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun localTime(millis: Long): String =
        clockFormat.format(Instant.ofEpochMilli(millis).atZone(zone))
}
