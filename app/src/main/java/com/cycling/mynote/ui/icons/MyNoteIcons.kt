package com.cycling.mynote.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** One drawable element of a Lucide glyph: its path data, and whether it is filled or stroked. */
private class LucidePath(val d: String, val filled: Boolean)

/**
 * Builds an icon from Lucide's elements, as stroke-drawn vector paths.
 *
 * Each element becomes its own path rather than one concatenated string: SVG resets the current
 * point per element, so merging them would let a later element's relative commands continue from
 * the previous element's end and draw somewhere else entirely.
 *
 * The colour always comes from the caller through `Icon(tint = ...)`; the black here is only a
 * placeholder that the tint replaces.
 */
private fun lucide(name: String, vararg paths: LucidePath): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { path ->
            addPath(
                pathData = PathParser().parsePathString(path.d).toNodes(),
                fill = if (path.filled) SolidColor(Color.Black) else null,
                stroke = if (path.filled) null else SolidColor(Color.Black),
                strokeLineWidth = if (path.filled) 0f else STROKE_WIDTH,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

/** Lucide's own stroke width for the 24 unit grid. */
private const val STROKE_WIDTH = 2f

/**
 * The subset of [Lucide](https://lucide.dev) the app draws, expressed as [ImageVector]s so only the
 * glyphs actually used are shipped instead of a full icon pack.
 */
object MyNoteIcons {

    /** `notebook-text` from Lucide (ISC). */
    val notebookText: ImageVector by lazy {
        lucide(
            "notebook-text",
            LucidePath("M2 6h4", filled = false),
            LucidePath("M2 10h4", filled = false),
            LucidePath("M2 14h4", filled = false),
            LucidePath("M2 18h4", filled = false),
            LucidePath("M6 2H18A2 2 0 0 1 20 4V20A2 2 0 0 1 18 22H6A2 2 0 0 1 4 20V4A2 2 0 0 1 6 2Z", filled = false),
            LucidePath("M9.5 8h5", filled = false),
            LucidePath("M9.5 12H16", filled = false),
            LucidePath("M9.5 16H14", filled = false),
        )
    }

    /** `search` from Lucide (ISC). */
    val search: ImageVector by lazy {
        lucide(
            "search",
            LucidePath("m21 21-4.34-4.34", filled = false),
            LucidePath("M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0", filled = false),
        )
    }

    /** `calendar-days` from Lucide (ISC). */
    val calendarDays: ImageVector by lazy {
        lucide(
            "calendar-days",
            LucidePath("M8 2v3", filled = false),
            LucidePath("M16 2v3", filled = false),
            LucidePath("M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3Z", filled = false),
            LucidePath("M3 9h18", filled = false),
            LucidePath("M8 13h.01", filled = false),
            LucidePath("M12 13h.01", filled = false),
            LucidePath("M16 13h.01", filled = false),
            LucidePath("M8 17h.01", filled = false),
            LucidePath("M12 17h.01", filled = false),
            LucidePath("M16 17h.01", filled = false),
        )
    }

    /** `settings` from Lucide (ISC). */
    val settings: ImageVector by lazy {
        lucide(
            "settings",
            LucidePath("M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915", filled = false),
            LucidePath("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", filled = false),
        )
    }

    /** `folder-tree` from Lucide (ISC). */
    val folderTree: ImageVector by lazy {
        lucide(
            "folder-tree",
            LucidePath("M20 10a1 1 0 0 0 1-1V6a1 1 0 0 0-1-1h-2.5a1 1 0 0 1-.8-.4l-.9-1.2A1 1 0 0 0 15 3h-2a1 1 0 0 0-1 1v5a1 1 0 0 0 1 1Z", filled = false),
            LucidePath("M20 21a1 1 0 0 0 1-1v-3a1 1 0 0 0-1-1h-2.9a1 1 0 0 1-.88-.55l-.42-.85a1 1 0 0 0-.92-.6H13a1 1 0 0 0-1 1v5a1 1 0 0 0 1 1Z", filled = false),
            LucidePath("M3 5a2 2 0 0 0 2 2h3", filled = false),
            LucidePath("M3 3v13a2 2 0 0 0 2 2h3", filled = false),
        )
    }

    /** `ellipsis-vertical` from Lucide (ISC). */
    val ellipsisVertical: ImageVector by lazy {
        lucide(
            "ellipsis-vertical",
            LucidePath("M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", filled = false),
            LucidePath("M11 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", filled = false),
            LucidePath("M11 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", filled = false),
        )
    }

    /** `arrow-up-down` from Lucide (ISC). */
    val arrowUpDown: ImageVector by lazy {
        lucide(
            "arrow-up-down",
            LucidePath("m21 16-4 4-4-4", filled = false),
            LucidePath("M17 20V4", filled = false),
            LucidePath("m3 8 4-4 4 4", filled = false),
            LucidePath("M7 4v16", filled = false),
        )
    }

    /** `list` from Lucide (ISC). */
    val list: ImageVector by lazy {
        lucide(
            "list",
            LucidePath("M3 5h.01", filled = false),
            LucidePath("M3 12h.01", filled = false),
            LucidePath("M3 19h.01", filled = false),
            LucidePath("M8 5h13", filled = false),
            LucidePath("M8 12h13", filled = false),
            LucidePath("M8 19h13", filled = false),
        )
    }

    /** `layout-grid` from Lucide (ISC). */
    val layoutGrid: ImageVector by lazy {
        lucide(
            "layout-grid",
            LucidePath("M4 3H9A1 1 0 0 1 10 4V9A1 1 0 0 1 9 10H4A1 1 0 0 1 3 9V4A1 1 0 0 1 4 3Z", filled = false),
            LucidePath("M15 3H20A1 1 0 0 1 21 4V9A1 1 0 0 1 20 10H15A1 1 0 0 1 14 9V4A1 1 0 0 1 15 3Z", filled = false),
            LucidePath("M15 14H20A1 1 0 0 1 21 15V20A1 1 0 0 1 20 21H15A1 1 0 0 1 14 20V15A1 1 0 0 1 15 14Z", filled = false),
            LucidePath("M4 14H9A1 1 0 0 1 10 15V20A1 1 0 0 1 9 21H4A1 1 0 0 1 3 20V15A1 1 0 0 1 4 14Z", filled = false),
        )
    }

    /** `plus` from Lucide (ISC). */
    val plus: ImageVector by lazy {
        lucide(
            "plus",
            LucidePath("M5 12h14", filled = false),
            LucidePath("M12 5v14", filled = false),
        )
    }

    /** `chevron-right` from Lucide (ISC). */
    val chevronRight: ImageVector by lazy {
        lucide(
            "chevron-right",
            LucidePath("m9 18 6-6-6-6", filled = false),
        )
    }

    /** `chevron-left` from Lucide (ISC). */
    val chevronLeft: ImageVector by lazy {
        lucide(
            "chevron-left",
            LucidePath("m15 18-6-6 6-6", filled = false),
        )
    }

    /** `chevron-down` from Lucide (ISC). */
    val chevronDown: ImageVector by lazy {
        lucide(
            "chevron-down",
            LucidePath("m6 9 6 6 6-6", filled = false),
        )
    }

    /** `x` from Lucide (ISC). */
    val x: ImageVector by lazy {
        lucide(
            "x",
            LucidePath("M18 6 6 18", filled = false),
            LucidePath("m6 6 12 12", filled = false),
        )
    }

    /** `check` from Lucide (ISC). */
    val check: ImageVector by lazy {
        lucide(
            "check",
            LucidePath("M20 6 9 17l-5-5", filled = false),
        )
    }

    /** `pin` from Lucide (ISC). */
    val pin: ImageVector by lazy {
        lucide(
            "pin",
            LucidePath("M12 17v5", filled = false),
            LucidePath("M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a1 1 0 0 1 1-1 2 2 0 0 0 0-4H8a2 2 0 0 0 0 4 1 1 0 0 1 1 1z", filled = false),
        )
    }

    /** `file-text` from Lucide (ISC). */
    val fileText: ImageVector by lazy {
        lucide(
            "file-text",
            LucidePath("M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z", filled = false),
            LucidePath("M14 2v5a1 1 0 0 0 1 1h5", filled = false),
            LucidePath("M10 9H8", filled = false),
            LucidePath("M16 13H8", filled = false),
            LucidePath("M16 17H8", filled = false),
        )
    }

    /** `feather` from Lucide (ISC). */
    val feather: ImageVector by lazy {
        lucide(
            "feather",
            LucidePath("M14.086 18.412A2 2 0 0112.67 19H5v-7.672a2 2 0 01.586-1.414L11.75 3.75a6 6 0 118.49 8.49z", filled = false),
            LucidePath("M16 8 2 22", filled = false),
            LucidePath("M17.488 15H9", filled = false),
        )
    }

    /** `folder-open` from Lucide (ISC). */
    val folderOpen: ImageVector by lazy {
        lucide(
            "folder-open",
            LucidePath("m6 14 1.5-2.9A2 2 0 0 1 9.24 10H20a2 2 0 0 1 1.94 2.5l-1.54 6a2 2 0 0 1-1.95 1.5H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h3.9a2 2 0 0 1 1.69.9l.81 1.2a2 2 0 0 0 1.67.9H18a2 2 0 0 1 2 2v2", filled = false),
        )
    }

    /** `folder` from Lucide (ISC). */
    val folder: ImageVector by lazy {
        lucide(
            "folder",
            LucidePath("M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z", filled = false),
        )
    }

    /** `folder-plus` from Lucide (ISC). */
    val folderPlus: ImageVector by lazy {
        lucide(
            "folder-plus",
            LucidePath("M12 10v6", filled = false),
            LucidePath("M9 13h6", filled = false),
            LucidePath("M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z", filled = false),
        )
    }

    /** `file-plus` from Lucide (ISC). */
    val filePlus: ImageVector by lazy {
        lucide(
            "file-plus",
            LucidePath("M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z", filled = false),
            LucidePath("M14 2v5a1 1 0 0 0 1 1h5", filled = false),
            LucidePath("M9 15h6", filled = false),
            LucidePath("M12 18v-6", filled = false),
        )
    }

    /** `history` from Lucide (ISC). */
    val history: ImageVector by lazy {
        lucide(
            "history",
            LucidePath("M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8", filled = false),
            LucidePath("M3 3v5h5", filled = false),
            LucidePath("M12 7v5l4 2", filled = false),
        )
    }

    /** `sun` from Lucide (ISC). */
    val sun: ImageVector by lazy {
        lucide(
            "sun",
            LucidePath("M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0", filled = false),
            LucidePath("M12 2v2", filled = false),
            LucidePath("M12 20v2", filled = false),
            LucidePath("m4.93 4.93 1.41 1.41", filled = false),
            LucidePath("m17.66 17.66 1.41 1.41", filled = false),
            LucidePath("M2 12h2", filled = false),
            LucidePath("M20 12h2", filled = false),
            LucidePath("m6.34 17.66-1.41 1.41", filled = false),
            LucidePath("m19.07 4.93-1.41 1.41", filled = false),
        )
    }

    /** `moon` from Lucide (ISC). */
    val moon: ImageVector by lazy {
        lucide(
            "moon",
            LucidePath("M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401", filled = false),
        )
    }

    /** `smartphone` from Lucide (ISC). */
    val smartphone: ImageVector by lazy {
        lucide(
            "smartphone",
            LucidePath("M7 2H17A2 2 0 0 1 19 4V20A2 2 0 0 1 17 22H7A2 2 0 0 1 5 20V4A2 2 0 0 1 7 2Z", filled = false),
            LucidePath("M12 18h.01", filled = false),
        )
    }

    /** `database` from Lucide (ISC). */
    val database: ImageVector by lazy {
        lucide(
            "database",
            LucidePath("M3 5a9 3 0 1 0 18 0a9 3 0 1 0 -18 0", filled = false),
            LucidePath("M3 5V19A9 3 0 0 0 21 19V5", filled = false),
            LucidePath("M3 12A9 3 0 0 0 21 12", filled = false),
        )
    }

    /** `trash-2` from Lucide (ISC). */
    val trash2: ImageVector by lazy {
        lucide(
            "trash-2",
            LucidePath("M10 11v6", filled = false),
            LucidePath("M14 11v6", filled = false),
            LucidePath("M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", filled = false),
            LucidePath("M3 6h18", filled = false),
            LucidePath("M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2", filled = false),
        )
    }

    /** `pencil` from Lucide (ISC). */
    val pencil: ImageVector by lazy {
        lucide(
            "pencil",
            LucidePath("M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z", filled = false),
            LucidePath("m15 5 4 4", filled = false),
        )
    }

    /** `info` from Lucide (ISC). */
    val info: ImageVector by lazy {
        lucide(
            "info",
            LucidePath("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0", filled = false),
            LucidePath("M12 16v-4", filled = false),
            LucidePath("M12 8h.01", filled = false),
        )
    }

    /** `archive` from Lucide (ISC). */
    val archive: ImageVector by lazy {
        lucide(
            "archive",
            LucidePath("M3 3H21A1 1 0 0 1 22 4V7A1 1 0 0 1 21 8H3A1 1 0 0 1 2 7V4A1 1 0 0 1 3 3Z", filled = false),
            LucidePath("M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8", filled = false),
            LucidePath("M10 12h4", filled = false),
        )
    }

    /** `refresh-cw` from Lucide (ISC). */
    val refreshCw: ImageVector by lazy {
        lucide(
            "refresh-cw",
            LucidePath("M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8", filled = false),
            LucidePath("M21 3v5h-5", filled = false),
            LucidePath("M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16", filled = false),
            LucidePath("M8 16H3v5", filled = false),
        )
    }

    /** `external-link` from Lucide (ISC). */
    val externalLink: ImageVector by lazy {
        lucide(
            "external-link",
            LucidePath("M15 3h6v6", filled = false),
            LucidePath("M10 14 21 3", filled = false),
            LucidePath("M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6", filled = false),
        )
    }

    /** `clock` from Lucide (ISC). */
    val clock: ImageVector by lazy {
        lucide(
            "clock",
            LucidePath("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0", filled = false),
            LucidePath("M12 6v6l4 2", filled = false),
        )
    }

    /** `tag` from Lucide (ISC). */
    val tag: ImageVector by lazy {
        lucide(
            "tag",
            LucidePath("M12.586 2.586A2 2 0 0 0 11.172 2H4a2 2 0 0 0-2 2v7.172a2 2 0 0 0 .586 1.414l8.704 8.704a2.426 2.426 0 0 0 3.42 0l6.58-6.58a2.426 2.426 0 0 0 0-3.42z", filled = false),
            LucidePath("M7 7.5a.5 .5 0 1 0 1 0a.5 .5 0 1 0 -1 0", filled = true),
        )
    }

    /** `wrap-text` from Lucide (ISC). */
    val wrapText: ImageVector by lazy {
        lucide(
            "wrap-text",
            LucidePath("m16 16-3 3 3 3", filled = false),
            LucidePath("M3 12h14.5a1 1 0 0 1 0 7H13", filled = false),
            LucidePath("M3 19h6", filled = false),
            LucidePath("M3 5h18", filled = false),
        )
    }

    /** `pencil-line` from Lucide (ISC). */
    val pencilLine: ImageVector by lazy {
        lucide(
            "pencil-line",
            LucidePath("M13 21h8", filled = false),
            LucidePath("m15 5 4 4", filled = false),
            LucidePath("M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z", filled = false),
        )
    }

    /** `columns-2` from Lucide (ISC). */
    val columns2: ImageVector by lazy {
        lucide(
            "columns-2",
            LucidePath("M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3Z", filled = false),
            LucidePath("M12 3v18", filled = false),
        )
    }

    /** `eye` from Lucide (ISC). */
    val eye: ImageVector by lazy {
        lucide(
            "eye",
            LucidePath("M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0", filled = false),
            LucidePath("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", filled = false),
        )
    }

    /** `heading-1` from Lucide (ISC). */
    val heading1: ImageVector by lazy {
        lucide(
            "heading-1",
            LucidePath("M4 12h8", filled = false),
            LucidePath("M4 18V6", filled = false),
            LucidePath("M12 18V6", filled = false),
            LucidePath("m17 12 3-2v8", filled = false),
        )
    }

    /** `bold` from Lucide (ISC). */
    val bold: ImageVector by lazy {
        lucide(
            "bold",
            LucidePath("M6 12h9a4 4 0 0 1 0 8H7a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1h7a4 4 0 0 1 0 8", filled = false),
        )
    }

    /** `italic` from Lucide (ISC). */
    val italic: ImageVector by lazy {
        lucide(
            "italic",
            LucidePath("M19 4L10 4", filled = false),
            LucidePath("M14 20L5 20", filled = false),
            LucidePath("M15 4L9 20", filled = false),
        )
    }

    /** `list-checks` from Lucide (ISC). */
    val listChecks: ImageVector by lazy {
        lucide(
            "list-checks",
            LucidePath("M13 5h8", filled = false),
            LucidePath("M13 12h8", filled = false),
            LucidePath("M13 19h8", filled = false),
            LucidePath("m3 17 2 2 4-4", filled = false),
            LucidePath("m3 7 2 2 4-4", filled = false),
        )
    }

    /** `text-quote` from Lucide (ISC). */
    val textQuote: ImageVector by lazy {
        lucide(
            "text-quote",
            LucidePath("M17 5H3", filled = false),
            LucidePath("M21 12H8", filled = false),
            LucidePath("M21 19H8", filled = false),
            LucidePath("M3 12v7", filled = false),
        )
    }

    /** `code` from Lucide (ISC). */
    val code: ImageVector by lazy {
        lucide(
            "code",
            LucidePath("m16 18 6-6-6-6", filled = false),
            LucidePath("m8 6-6 6 6 6", filled = false),
        )
    }

    /** `link` from Lucide (ISC). */
    val link: ImageVector by lazy {
        lucide(
            "link",
            LucidePath("M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71", filled = false),
            LucidePath("M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71", filled = false),
        )
    }

    /** `image` from Lucide (ISC). */
    val image: ImageVector by lazy {
        lucide(
            "image",
            LucidePath("M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3Z", filled = false),
            LucidePath("M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", filled = false),
            LucidePath("m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21", filled = false),
        )
    }

    /** `corner-down-left` from Lucide (ISC). */
    val cornerDownLeft: ImageVector by lazy {
        lucide(
            "corner-down-left",
            LucidePath("M20 4v7a4 4 0 0 1-4 4H4", filled = false),
            LucidePath("m9 10-5 5 5 5", filled = false),
        )
    }

    /** `save` from Lucide (ISC). */
    val save: ImageVector by lazy {
        lucide(
            "save",
            LucidePath("M15.2 3a2 2 0 0 1 1.4.6l3.8 3.8a2 2 0 0 1 .6 1.4V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", filled = false),
            LucidePath("M17 21v-7a1 1 0 0 0-1-1H8a1 1 0 0 0-1 1v7", filled = false),
            LucidePath("M7 3v4a1 1 0 0 0 1 1h7", filled = false),
        )
    }

    /** `zap` from Lucide (ISC). */
    val zap: ImageVector by lazy {
        lucide(
            "zap",
            LucidePath("M15.914 4a1.5 1.5 0 00-2.474-1.561l-9 9A1.5 1.5 0 005.5 14h4.002a.5.5 0 01.471.666L8.086 20a1.5 1.5 0 002.475 1.56l9-9A1.5 1.5 0 0018.5 10h-3.997a.5.5 0 01-.472-.667z", filled = false),
        )
    }

    /** `globe` from Lucide (ISC). */
    val globe: ImageVector by lazy {
        lucide(
            "globe",
            LucidePath("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0", filled = false),
            LucidePath("M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20", filled = false),
            LucidePath("M2 12h20", filled = false),
        )
    }

    /** `message-circle` from Lucide (ISC). */
    val messageCircle: ImageVector by lazy {
        lucide(
            "message-circle",
            LucidePath("M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719", filled = false),
        )
    }

    /** `notebook-pen` from Lucide (ISC). */
    val notebookPen: ImageVector by lazy {
        lucide(
            "notebook-pen",
            LucidePath("M13.4 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-7.4", filled = false),
            LucidePath("M2 6h4", filled = false),
            LucidePath("M2 10h4", filled = false),
            LucidePath("M2 14h4", filled = false),
            LucidePath("M2 18h4", filled = false),
            LucidePath("M21.378 5.626a1 1 0 1 0-3.004-3.004l-5.01 5.012a2 2 0 0 0-.506.854l-.837 2.87a.5.5 0 0 0 .62.62l2.87-.837a2 2 0 0 0 .854-.506z", filled = false),
        )
    }

    /** `share-2` from Lucide (ISC). */
    val share2: ImageVector by lazy {
        lucide(
            "share-2",
            LucidePath("M15 5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", filled = false),
            LucidePath("M3 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", filled = false),
            LucidePath("M15 19a3 3 0 1 0 6 0a3 3 0 1 0 -6 0", filled = false),
            LucidePath("M8.59 13.51L15.42 17.49", filled = false),
            LucidePath("M15.41 6.51L8.59 10.49", filled = false),
        )
    }

    /** `more-horizontal` from Lucide (ISC). */
    val moreHorizontal: ImageVector by lazy {
        lucide(
            "more-horizontal",
            LucidePath("M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", filled = false),
            LucidePath("M18 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", filled = false),
            LucidePath("M4 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0", filled = false),
        )
    }

    /** `calendar-plus` from Lucide (ISC). */
    val calendarPlus: ImageVector by lazy {
        lucide(
            "calendar-plus",
            LucidePath("M16 18h6", filled = false),
            LucidePath("M16 2v3", filled = false),
            LucidePath("M19 15v6", filled = false),
            LucidePath("M21 11.5V5a2 2 0 00-2-2H5a2 2 0 00-2 2v14a2 2 0 002 2h8.3", filled = false),
            LucidePath("M3 9h18", filled = false),
            LucidePath("M8 2v3", filled = false),
        )
    }

    /** `inbox` from Lucide (ISC). */
    val inbox: ImageVector by lazy {
        lucide(
            "inbox",
            LucidePath("M22L12L16L12L14L15L10L15L8L12L2L12", filled = false),
            LucidePath("M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z", filled = false),
        )
    }

    /** `cloud` from Lucide (ISC). */
    val cloud: ImageVector by lazy {
        lucide(
            "cloud",
            LucidePath("M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z", filled = false),
        )
    }

    /** `alert-circle` from Lucide (ISC). */
    val alertCircle: ImageVector by lazy {
        lucide(
            "alert-circle",
            LucidePath("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0", filled = false),
            LucidePath("M12 8L12 12", filled = false),
            LucidePath("M12 16L12.01 16", filled = false),
        )
    }

    /** `loader` from Lucide (ISC). */
    val loader: ImageVector by lazy {
        lucide(
            "loader",
            LucidePath("M12 2v4", filled = false),
            LucidePath("m16.2 7.8 2.9-2.9", filled = false),
            LucidePath("M18 12h4", filled = false),
            LucidePath("m16.2 16.2 2.9 2.9", filled = false),
            LucidePath("M12 18v4", filled = false),
            LucidePath("m4.9 19.1 2.9-2.9", filled = false),
            LucidePath("M2 12h4", filled = false),
            LucidePath("m4.9 4.9 2.9 2.9", filled = false),
        )
    }

    /** `type` from Lucide (ISC). */
    val type: ImageVector by lazy {
        lucide(
            "type",
            LucidePath("M12 4v16", filled = false),
            LucidePath("M4 7V5a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v2", filled = false),
            LucidePath("M9 20h6", filled = false),
        )
    }

    /** `undo-2` from Lucide (ISC). */
    val undo2: ImageVector by lazy {
        lucide(
            "undo-2",
            LucidePath("M9 14 4 9l5-5", filled = false),
            LucidePath("M4 9h10.5a5.5 5.5 0 0 1 5.5 5.5a5.5 5.5 0 0 1-5.5 5.5H11", filled = false),
        )
    }

    /** `redo-2` from Lucide (ISC). */
    val redo2: ImageVector by lazy {
        lucide(
            "redo-2",
            LucidePath("m15 14 5-5-5-5", filled = false),
            LucidePath("M20 9H9.5A5.5 5.5 0 0 0 4 14.5A5.5 5.5 0 0 0 9.5 20H13", filled = false),
        )
    }

    /** `terminal` from Lucide (ISC). */
    val terminal: ImageVector by lazy {
        lucide(
            "terminal",
            LucidePath("M12 19h8", filled = false),
            LucidePath("m4 17 6-6-6-6", filled = false),
        )
    }
}
