package com.cycling.mynote.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownImagesTest {

    @Test
    fun `an http address is remote`() {
        assertEquals(
            ImageReference.Remote("https://e.com/a.png"),
            MarkdownImages.resolve("https://e.com/a.png", "日记"),
        )
        assertEquals(
            ImageReference.Remote("http://e.com/a.png"),
            MarkdownImages.resolve("http://e.com/a.png", ""),
        )
    }

    @Test
    fun `a relative path is resolved against the note's folder`() {
        assertEquals(
            ImageReference.RepoFile("日记/img/a.png"),
            MarkdownImages.resolve("img/a.png", "日记"),
        )
        assertEquals(
            ImageReference.RepoFile("日记/a.png"),
            MarkdownImages.resolve("./a.png", "日记"),
        )
    }

    @Test
    fun `two dots walk up out of the note's folder`() {
        assertEquals(
            ImageReference.RepoFile("日记/assets/a.png"),
            MarkdownImages.resolve("../assets/a.png", "日记/2025"),
        )
        assertEquals(
            ImageReference.RepoFile("assets/a.png"),
            MarkdownImages.resolve("../../assets/a.png", "日记/2025"),
        )
    }

    @Test
    fun `a leading slash means the repository root`() {
        assertEquals(
            ImageReference.RepoFile("assets/a.png"),
            MarkdownImages.resolve("/assets/a.png", "日记/2026"),
        )
    }

    @Test
    fun `a reference cannot escape the repository`() {
        // Nothing above the root to walk up to, so the extra `..` is dropped rather than resolved.
        assertEquals(
            ImageReference.RepoFile("a.png"),
            MarkdownImages.resolve("../../../../a.png", "日记"),
        )
    }

    @Test
    fun `a space written as a url escape is read back`() {
        assertEquals(
            ImageReference.RepoFile("日记/我的 图.png"),
            MarkdownImages.resolve("我的%20图.png", "日记"),
        )
    }

    @Test
    fun `something the app cannot read is external`() {
        assertEquals(
            ImageReference.External("content://media/1"),
            MarkdownImages.resolve("content://media/1", ""),
        )
        assertEquals(
            ImageReference.External("file:///sdcard/a.png"),
            MarkdownImages.resolve("file:///sdcard/a.png", ""),
        )
    }

    @Test
    fun `a note writes the shortest reference that still resolves`() {
        assertEquals("pic.png", MarkdownImages.relative("日记/pic.png", "日记"))
        assertEquals("attachments/pic.png", MarkdownImages.relative("attachments/pic.png", ""))
        assertEquals("../attachments/pic.png", MarkdownImages.relative("attachments/pic.png", "日记"))
        assertEquals("../../attachments/pic.png", MarkdownImages.relative("attachments/pic.png", "日记/2025"))
        assertEquals("2025/pic.png", MarkdownImages.relative("日记/2025/pic.png", "日记"))
    }

    @Test
    fun `a written reference resolves back to the path it was written for`() {
        val target = "attachments/我的 图.png"
        for (folder in listOf("", "日记", "日记/2025", "其他")) {
            assertEquals(
                ImageReference.RepoFile(target),
                MarkdownImages.resolve(MarkdownImages.relative(target, folder), folder),
            )
        }
    }

    @Test
    fun `an address on the device is not inside the repository`() {
        // It resolves as a repo path — the app has no way to know it is not one — and simply fails to
        // load, which the preview shows as a placeholder rather than as a broken picture.
        assertEquals(
            ImageReference.RepoFile("sdcard/Download/a.png"),
            MarkdownImages.resolve("/sdcard/Download/a.png", ""),
        )
    }
}
