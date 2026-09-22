package com.cycling.mynote.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteSizeFormatterTest {

    @Test
    fun `bytes below a kilobyte are shown as bytes`() {
        assertEquals("0 B", ByteSizeFormatter.format(0))
        assertEquals("512 B", ByteSizeFormatter.format(512))
        assertEquals("1023 B", ByteSizeFormatter.format(1023))
    }

    @Test
    fun `kilobytes and megabytes get one decimal place when it is informative`() {
        assertEquals("1 KB", ByteSizeFormatter.format(1024))
        assertEquals("1.5 KB", ByteSizeFormatter.format(1536))
        assertEquals("24.6 MB", ByteSizeFormatter.format((24.6 * 1024 * 1024).toLong()))
    }

    @Test
    fun `gigabytes are shown for very large folders`() {
        assertEquals("2 GB", ByteSizeFormatter.format(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `whole numbers drop the trailing zero`() {
        assertEquals("5 MB", ByteSizeFormatter.format(5L * 1024 * 1024))
    }
}
