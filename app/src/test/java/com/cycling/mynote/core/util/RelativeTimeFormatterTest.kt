package com.cycling.mynote.core.util

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeFormatterTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val formatter = RelativeTimeFormatter(zone)

    /** 2025-03-12 is a Wednesday, so relative weekday names are predictable. */
    private val now = millis(2025, 3, 12, 15, 0)

    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
    ): Long = LocalDateTime.of(year, month, day, hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun `note row shows minutes under an hour`() {
        assertEquals("刚刚", formatter.forNoteRow(now - 30_000, now))
        assertEquals("12 分钟前", formatter.forNoteRow(now - 12 * 60_000, now))
        assertEquals("59 分钟前", formatter.forNoteRow(now - 59 * 60_000, now))
    }

    @Test
    fun `note row shows a clock time for today`() {
        assertEquals("今天 09:24", formatter.forNoteRow(millis(2025, 3, 12, 9, 24), now))
    }

    @Test
    fun `note row names yesterday`() {
        assertEquals("昨天 21:07", formatter.forNoteRow(millis(2025, 3, 11, 21, 7), now))
    }

    @Test
    fun `note row names the weekday within the last week`() {
        // now is 2025-03-12, a Wednesday, so two days back is Monday and one day back is 昨天.
        assertEquals("周一 18:02", formatter.forNoteRow(millis(2025, 3, 10, 18, 2), now))
        assertEquals("周日 18:02", formatter.forNoteRow(millis(2025, 3, 9, 18, 2), now))
    }

    @Test
    fun `note row falls back to a date beyond a week`() {
        assertEquals("3月1日", formatter.forNoteRow(millis(2025, 3, 1, 10, 0), now))
        assertEquals("2024年12月31日", formatter.forNoteRow(millis(2024, 12, 31), now))
    }

    @Test
    fun `yesterday is a calendar day, not 24 hours ago`() {
        // 25 hours before 00:30 is two calendar days back, which must read as a weekday, not 昨天.
        val justAfterMidnight = millis(2025, 3, 12, 0, 30)
        assertEquals("周一 23:30", formatter.forNoteRow(millis(2025, 3, 10, 23, 30), justAfterMidnight))
        assertEquals("昨天 23:30", formatter.forNoteRow(millis(2025, 3, 11, 23, 30), justAfterMidnight))
    }

    @Test
    fun `a future timestamp never reads as negative minutes`() {
        assertEquals("刚刚", formatter.forNoteRow(now + 60_000, now))
    }

    @Test
    fun `search results use a coarser scale`() {
        assertEquals("30 分钟前", formatter.forSearchResult(now - 30 * 60_000, now))
        assertEquals("5 小时前", formatter.forSearchResult(now - 5 * 3_600_000, now))
        assertEquals("2 天前", formatter.forSearchResult(millis(2025, 3, 10, 12, 0), now))
        assertEquals("6 天前", formatter.forSearchResult(millis(2025, 3, 6, 12, 0), now))
    }

    @Test
    fun `search results collapse a fortnight into last week`() {
        assertEquals("上周", formatter.forSearchResult(millis(2025, 3, 1, 12, 0), now))
        assertEquals("2 周前", formatter.forSearchResult(millis(2025, 2, 25, 12, 0), now))
    }

    @Test
    fun `tree rows show the shortest form`() {
        assertEquals("今天", formatter.forTreeRow(millis(2025, 3, 12, 8, 0), now))
        assertEquals("昨天", formatter.forTreeRow(millis(2025, 3, 11, 8, 0), now))
        assertEquals("周一", formatter.forTreeRow(millis(2025, 3, 10, 8, 0), now))
        assertEquals("8 天前", formatter.forTreeRow(millis(2025, 3, 4, 8, 0), now))
    }

    @Test
    fun `absolute date is stable`() {
        assertEquals("2025年3月12日", formatter.absoluteDate(millis(2025, 3, 12)))
    }

    @Test
    fun `save stamp reads naturally at each scale`() {
        assertEquals("刚刚保存", formatter.forSaveStamp(now - 10_000, now))
        assertEquals("5 分钟前保存", formatter.forSaveStamp(now - 5 * 60_000, now))
        assertEquals("今天 09:24 保存", formatter.forSaveStamp(millis(2025, 3, 12, 9, 24), now))
    }
}
