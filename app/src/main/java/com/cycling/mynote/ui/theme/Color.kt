package com.cycling.mynote.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's colour roles, mirroring the `bg`/`surface`/`text-*`/`accent` variables of the design
 * file one for one so the two can never drift apart.
 *
 * Deliberately not wired through Material's [androidx.compose.material3.ColorScheme]: the design
 * uses a flat Notion-like palette where `bg` and `surface` are the same colour in light mode, which
 * does not map onto Material's elevation-driven container hierarchy.
 */
@Immutable
data class MyNoteColors(
    val bg: Color,
    val surface: Color,
    val surfaceSunken: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentTint: Color,
    val onAccent: Color,
    val border: Color,
    val danger: Color,
    val scrim: Color,
)

/** `mode = light` values of the design file's variables. */
val LightMyNoteColors = MyNoteColors(
    bg = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFF7F7F5),
    textPrimary = Color(0xFF37352F),
    textSecondary = Color(0xFF787774),
    textTertiary = Color(0xFF9B9A97),
    accent = Color(0xFF2383E2),
    accentTint = Color(0xFFE7F0FA),
    onAccent = Color(0xFFFFFFFF),
    border = Color(0xFFE9E9E7),
    danger = Color(0xFFD44C47),
    scrim = Color(0xFF000000),
)

/** `mode = dark` values of the design file's variables. */
val DarkMyNoteColors = MyNoteColors(
    bg = Color(0xFF191919),
    surface = Color(0xFF252525),
    surfaceSunken = Color(0xFF2F2F2F),
    textPrimary = Color(0xFFE9E9E7),
    textSecondary = Color(0xFF9B9B9B),
    textTertiary = Color(0xFF6F6F6F),
    accent = Color(0xFF529CCA),
    accentTint = Color(0xFF1F3A52),
    onAccent = Color(0xFFFFFFFF),
    border = Color(0xFF2F2F2F),
    danger = Color(0xFFD44C47),
    scrim = Color(0xFF000000),
)

val LocalMyNoteColors = staticCompositionLocalOf { LightMyNoteColors }
