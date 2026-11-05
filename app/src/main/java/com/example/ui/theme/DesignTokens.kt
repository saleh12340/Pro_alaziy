package com.example.ui.theme

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Centralized Design Tokens for Thermal Invoice & Accounting App.
 * Ensures consistent spacing, corner radii, minimum touch targets, and typography.
 */
object DesignTokens {
    // Spacing
    val SpacingXxs: Dp = 2.dp
    val SpacingXs: Dp = 4.dp
    val SpacingS: Dp = 8.dp
    val SpacingM: Dp = 12.dp
    val SpacingL: Dp = 16.dp
    val SpacingXl: Dp = 24.dp
    val SpacingXxl: Dp = 32.dp

    // Corner Radii
    val RadiusS: Dp = 6.dp
    val RadiusM: Dp = 10.dp
    val RadiusL: Dp = 14.dp
    val RadiusXl: Dp = 20.dp
    val RadiusFull: Dp = 999.dp

    // Touch Targets (Android Accessibility Standard >= 48dp)
    val MinTouchTarget: Dp = 48.dp

    // Typography Helper for Arabic (disabling includeFontPadding avoids unwanted vertical clipping)
    fun arabicTextStyle(
        baseStyle: TextStyle = TextStyle.Default
    ): TextStyle {
        return baseStyle.copy(
            platformStyle = PlatformTextStyle(
                includeFontPadding = false
            )
        )
    }
}
