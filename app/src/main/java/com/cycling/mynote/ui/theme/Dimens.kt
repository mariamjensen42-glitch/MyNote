package com.cycling.mynote.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The spacing and radius scale of the design file, named by role.
 *
 * The design only formalises three radii (`r-sm` 4, `r-md` 8, `r-lg` 14) and leaves the rest inline.
 * Only the values more than one screen actually shares are named here; the rest stay as literals at
 * their use site, where their context explains them better than a name would.
 */
@Immutable
data class MyNoteDimens(
    // Screen frame
    val headerPaddingTop: Dp = 10.dp,

    // Cards and rows
    val cardPadding: Dp = 16.dp,
    val rowPaddingHorizontal: Dp = 16.dp,
    val rowPaddingVertical: Dp = 14.dp,
    val rowMinHeight: Dp = 48.dp,

    // Gaps
    val gapTiny: Dp = 2.dp,
    val gapExtraSmall: Dp = 4.dp,
    val gapSmall: Dp = 6.dp,
    val gapCompact: Dp = 8.dp,
    val gapMedium: Dp = 10.dp,
    val gapRegular: Dp = 12.dp,
    val gapLarge: Dp = 16.dp,
    val gapSection: Dp = 18.dp,
    val gapExtraLarge: Dp = 24.dp,
    val gapHero: Dp = 28.dp,

    // Radii
    val radiusSmall: Dp = 4.dp,
    val radiusMedium: Dp = 8.dp,
    val radiusTab: Dp = 12.dp,
    val radiusCapsule: Dp = 18.dp,
    val radiusPill: Dp = 28.dp,

    // Controls
    val iconButtonSize: Dp = 36.dp,
    val iconButtonSizeLarge: Dp = 38.dp,
    val controlHeight: Dp = 42.dp,
    val buttonHeight: Dp = 48.dp,
    val chipHeight: Dp = 28.dp,
    val tagHeight: Dp = 20.dp,
    val tabBarHeight: Dp = 56.dp,
    val fabSize: Dp = 56.dp,
    val switchWidth: Dp = 44.dp,
    val switchHeight: Dp = 26.dp,
    val switchKnobSize: Dp = 20.dp,

    // Icon sizes
    val iconExtraSmall: Dp = 11.dp,
    val iconSmall: Dp = 13.dp,
    val iconCompact: Dp = 15.dp,
    val iconMedium: Dp = 17.dp,
    val iconRegular: Dp = 18.dp,
    val iconLarge: Dp = 20.dp,
    val iconExtraLarge: Dp = 26.dp,

    // Hairlines
    val hairline: Dp = 1.dp,
)

val LocalMyNoteDimens = staticCompositionLocalOf { MyNoteDimens() }
