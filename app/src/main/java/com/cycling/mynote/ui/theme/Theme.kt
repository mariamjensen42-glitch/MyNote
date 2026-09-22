package com.cycling.mynote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

/**
 * Point of access for the design tokens, following the "extended MaterialTheme" pattern from the
 * Compose theming guidance: screens read `MyNoteTheme.text.noteTitle` rather than threading a
 * typography object through every composable.
 */
object MyNoteTheme {
    val colors: MyNoteColors
        @Composable @ReadOnlyComposable get() = LocalMyNoteColors.current

    val text: MyNoteTextStyles
        @Composable @ReadOnlyComposable get() = LocalMyNoteTextStyles.current

    val dimens: MyNoteDimens
        @Composable @ReadOnlyComposable get() = LocalMyNoteDimens.current
}

/**
 * Material's own colour scheme is kept only as a carrier for the handful of Material surfaces the
 * app does not draw itself (dialogs, text-selection handles). Everything visible comes from
 * [MyNoteColors].
 */
private fun materialScheme(colors: MyNoteColors, dark: Boolean) = with(colors) {
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    scheme.copy(
        primary = accent,
        onPrimary = onAccent,
        background = bg,
        onBackground = textPrimary,
        surface = surface,
        onSurface = textPrimary,
        surfaceVariant = surfaceSunken,
        onSurfaceVariant = textSecondary,
        outline = border,
        error = danger,
    )
}

/**
 * @param darkTheme resolved theme, not the raw system setting — the app supports an explicit
 *   light/dark override, so the caller decides and passes the answer in.
 * @param fontKey which platform font family the editor and UI should use.
 */
@Composable
fun MyNoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    fontKey: String = "sans",
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkMyNoteColors else LightMyNoteColors
    val textStyles = remember(fontKey) { defaultMyNoteTextStyles(fontKey) }

    CompositionLocalProvider(
        LocalMyNoteColors provides colors,
        LocalMyNoteTextStyles provides textStyles,
        LocalMyNoteDimens provides MyNoteDimens(),
    ) {
        MaterialTheme(
            colorScheme = materialScheme(colors, darkTheme),
            content = content,
        )
    }
}
