package com.cycling.mynote.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Builds every icon in [MyNoteIcons].
 *
 * Important because the icons are declared as `by lazy` inside an `object`, and the editor's format
 * toolbar holds a top-level `val` that touches a dozen of them. A single unparseable path therefore
 * does not fail where the icon is drawn — it fails in that file's static initializer, taking the
 * whole editor composable with it as an `ExceptionInInitializerError` that names nothing useful.
 * Touching each one here turns that into a one-line failure.
 */
class MyNoteIconsTest {

    /**
     * `by lazy` compiles to a getter plus a `Lazy` backing field, so the icons are reachable as
     * zero-argument getters returning [ImageVector] rather than as fields of that type.
     */
    private fun iconGetters() = MyNoteIcons::class.java.methods
        .filter {
            it.parameterCount == 0 &&
                it.returnType == ImageVector::class.java &&
                it.name.startsWith("get")
        }
        .sortedBy { it.name }

    @Test
    fun `every icon parses into an image vector`() {
        val getters = iconGetters()
        assertTrue("MyNoteIcons exposes no icons", getters.isNotEmpty())

        val failures = getters.mapNotNull { getter ->
            runCatching { getter.invoke(MyNoteIcons) }
                .exceptionOrNull()
                ?.let { "${getter.name}: ${it.cause ?: it}" }
        }

        assertTrue("Icons that failed to build:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    @Test
    fun `every icon uses the shared 24 unit viewport`() {
        iconGetters().forEach { getter ->
            val icon = getter.invoke(MyNoteIcons) as ImageVector
            assertTrue(
                "${getter.name} viewport is ${icon.viewportWidth}x${icon.viewportHeight}",
                icon.viewportWidth == 24f && icon.viewportHeight == 24f,
            )
            assertTrue("${getter.name} has no paths", icon.root.size > 0)
        }
    }

    /**
     * Guards the generator's one-path-per-element rule.
     *
     * Lucide's `x` is two elements, and merging them into one path string made the second element's
     * relative `m` continue from the first element's end — the glyph rendered as a single diagonal
     * stroke. An icon count assertion is what would catch that regressing.
     */
    @Test
    fun `multi-element icons keep one path per svg element`() {
        val x = MyNoteIcons.x
        assertEquals("x should be two elements", 2, x.root.size)

        val share = MyNoteIcons.share2
        assertEquals("share-2 should be five elements", 5, share.root.size)
    }
}
