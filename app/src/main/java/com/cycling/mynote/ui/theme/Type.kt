package com.cycling.mynote.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Named text styles for every typographic role in the design, so screens pick a role instead of
 * re-deriving a size/weight/line-height triple that then drifts from its neighbours.
 *
 * Line heights are absolute sp values converted from the design's ratios (the design file's
 * `lineHeight` is relative to the font size) because that is what [TextStyle] takes.
 *
 * No font files ship with the app: the design's `font-body` is Inter but its copy is mostly
 * Chinese, which falls back to the platform CJK face anyway, so a bundled Latin-only Inter would
 * render inconsistently next to Chinese glyphs. The editor's font *is* user-selectable in settings,
 * so the families are injected here rather than hard-coded.
 */
@Immutable
class MyNoteTextStyles(
    private val bodyFont: FontFamily,
    private val monoFont: FontFamily,
) {
    val displayTitle = TextStyle(
        fontFamily = bodyFont,
        fontSize = 27.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 34.sp,
    )
    val screenTitle = TextStyle(
        fontFamily = bodyFont,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 29.sp,
    )
    val screenTitleMedium = TextStyle(
        fontFamily = bodyFont,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 29.sp,
    )
    val screenSubtitle = TextStyle(
        fontFamily = bodyFont,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
    val sheetTitle = TextStyle(
        fontFamily = bodyFont,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 23.sp,
    )
    val appName = TextStyle(
        fontFamily = bodyFont,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp,
    )

    val sectionLabel = TextStyle(
        fontFamily = bodyFont,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        lineHeight = 17.sp,
    )
    val sectionTitle = TextStyle(
        fontFamily = bodyFont,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp,
    )
    val sectionMeta = TextStyle(fontFamily = bodyFont, fontSize = 11.sp, lineHeight = 15.sp)

    val noteTitle = TextStyle(
        fontFamily = bodyFont,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
    )
    val noteTitleLarge = TextStyle(
        fontFamily = bodyFont,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 21.sp,
    )
    val snippet = TextStyle(fontFamily = bodyFont, fontSize = 13.sp, lineHeight = 18.sp)
    val meta = TextStyle(fontFamily = bodyFont, fontSize = 11.sp, lineHeight = 15.sp)

    val body = TextStyle(fontFamily = bodyFont, fontSize = 14.5.sp, lineHeight = 22.sp)
    val bodySmall = TextStyle(fontFamily = bodyFont, fontSize = 13.sp, lineHeight = 20.sp)
    val bodyTight = TextStyle(fontFamily = bodyFont, fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontFamily = bodyFont, fontSize = 11.5.sp, lineHeight = 16.sp)
    val micro = TextStyle(fontFamily = bodyFont, fontSize = 10.5.sp, lineHeight = 14.sp)
    val hint = TextStyle(fontFamily = bodyFont, fontSize = 11.5.sp, lineHeight = 16.sp)

    val tabLabel = TextStyle(
        fontFamily = bodyFont,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 13.sp,
    )
    val tabLabelActive = TextStyle(
        fontFamily = bodyFont,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 13.sp,
    )

    val chip = TextStyle(
        fontFamily = bodyFont,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 17.sp,
    )
    val chipActive = TextStyle(
        fontFamily = bodyFont,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 17.sp,
    )
    val tag = TextStyle(fontFamily = bodyFont, fontSize = 11.sp, lineHeight = 15.sp)
    val tagAccent = TextStyle(fontFamily = bodyFont, fontSize = 12.sp, lineHeight = 16.sp)

    val button = TextStyle(
        fontFamily = bodyFont,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 21.sp,
    )
    val buttonSmall = TextStyle(
        fontFamily = bodyFont,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
    )
    val buttonMedium = TextStyle(
        fontFamily = bodyFont,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
    )

    val rowLabel = TextStyle(fontFamily = bodyFont, fontSize = 15.sp, lineHeight = 20.sp)
    val rowLabelStrong = TextStyle(
        fontFamily = bodyFont,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
    )
    val rowValue = TextStyle(fontFamily = bodyFont, fontSize = 13.sp, lineHeight = 18.sp)
    val rowAction = TextStyle(
        fontFamily = bodyFont,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 18.sp,
    )
    val actionLabel = TextStyle(
        fontFamily = bodyFont,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
    )
    val badge = TextStyle(fontFamily = bodyFont, fontSize = 10.sp, lineHeight = 14.sp)

    val monoBody = TextStyle(fontFamily = monoFont, fontSize = 13.5.sp, lineHeight = 21.sp)
    val monoHeading = TextStyle(
        fontFamily = monoFont,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 21.sp,
    )
    val monoSubheading = TextStyle(
        fontFamily = monoFont,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 19.sp,
    )
    val monoSmall = TextStyle(fontFamily = monoFont, fontSize = 12.5.sp, lineHeight = 18.sp)
    val monoSmallStrong = TextStyle(
        fontFamily = monoFont,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp,
    )
    val monoCode = TextStyle(fontFamily = monoFont, fontSize = 12.sp, lineHeight = 17.sp)
    val monoMicro = TextStyle(fontFamily = monoFont, fontSize = 10.5.sp, lineHeight = 14.sp)
    val monoTag = TextStyle(
        fontFamily = monoFont,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 15.sp,
    )
}

/** The default pairing: platform sans for UI text, platform monospace for Markdown source. */
fun defaultMyNoteTextStyles(bodyFamilyKey: String): MyNoteTextStyles =
    MyNoteTextStyles(fontFamilyFor(bodyFamilyKey), FontFamily.Monospace)

/** Maps an [com.cycling.mynote.core.model.EditorFont] key onto a platform font family. */
fun fontFamilyFor(key: String): FontFamily = when (key) {
    "sans" -> FontFamily.SansSerif
    "serif" -> FontFamily.Serif
    "mono" -> FontFamily.Monospace
    else -> FontFamily.Default
}

val LocalMyNoteTextStyles = staticCompositionLocalOf { defaultMyNoteTextStyles("sans") }
