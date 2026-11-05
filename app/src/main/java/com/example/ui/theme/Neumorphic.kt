package com.example.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// NATURAL PROFESSIONAL COLOR PALETTE (Thermal Receipt Natural Light Mode)
// ============================================================================
val NeuBackground = Color(0xFFF8FAFC)        // Clean natural paper canvas (Slate 50)
val NeuSurface = Color(0xFFFFFFFF)           // Pure white card surface
val NeuSurfaceRaised = Color(0xFFFFFFFF)     // Clean white elevated surface
val NeuDarkShadow = Color(0x140F172A)        // Soft natural subtle shadow
val NeuLightHighlight = Color(0xFFE2E8F0)    // Clean crisp neutral border
val NeuInsetBg = Color(0xFFF1F5F9)           // Soft recessed input field background
val NeuInsetBorder = Color(0xFFCBD5E1)       // Natural input border

val NeuAccentBlue = Color(0xFF1E3A8A)        // Classic thermal navy (ReceiptInkNavy)
val NeuAccentBlueHover = Color(0xFF2563EB)   // Professional blue accent
val NeuAccentBlueGlow = Color(0x1A1E3A8A)    // Soft subtle tint

val NeuTextPrimary = Color(0xFF0F172A)       // Crisp dark slate text (Slate 900)
val NeuTextSecondary = Color(0xFF475569)     // Slate 600 secondary text
val NeuTextMuted = Color(0xFF94A3B8)         // Slate 400 muted text

val NeuSuccess = Color(0xFF059669)           // Natural emerald
val NeuError = Color(0xFFDC2626)             // Natural crimson alert

// ============================================================================
// NEUMORPHIC REUSABLE COMPOSABLES (Natural, Crisp & Professional)
// ============================================================================

/**
 * Natural Raised Card with soft subtle elevation and clean crisp border.
 */
@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    backgroundColor: Color = NeuSurface,
    elevation: Dp = 2.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = backgroundColor,
        shadowElevation = elevation,
        border = BorderStroke(1.dp, NeuLightHighlight)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Sunken / Inset Box for TextFields and debossed controls with natural light styling.
 */
@Composable
fun NeuInsetBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    backgroundColor: Color = NeuInsetBg,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(
                border = BorderStroke(1.dp, NeuInsetBorder),
                shape = shape
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * Primary & Secondary Button with clean natural thermal styling.
 */
@Composable
fun NeuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(10.dp),
    isPrimary: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    if (isPrimary) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeuAccentBlue,
                contentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = NeuSurface,
                contentColor = NeuTextPrimary
            ),
            border = BorderStroke(1.dp, NeuInsetBorder)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content
            )
        }
    }
}

/**
 * Circular Action Button.
 */
@Composable
fun NeuCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    isPrimary: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    if (isPrimary) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(size),
            shape = CircleShape,
            color = NeuAccentBlue,
            shadowElevation = 3.dp
        ) {
            Box(contentAlignment = Alignment.Center, content = content)
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.size(size),
            shape = CircleShape,
            color = NeuSurface,
            shadowElevation = 1.dp,
            border = BorderStroke(1.dp, NeuLightHighlight)
        ) {
            Box(contentAlignment = Alignment.Center, content = content)
        }
    }
}

/**
 * Natural Badge / Icon Container.
 */
@Composable
fun NeuBadge(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = NeuSurface,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, NeuLightHighlight)
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}

/**
 * Compact Icon Box to neatly contain symbols/icons inside cards and rows.
 */
@Composable
fun NeuIconBox(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    shape: Shape = RoundedCornerShape(8.dp),
    backgroundColor: Color = NeuInsetBg,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, NeuLightHighlight)
    ) {
        Box(contentAlignment = Alignment.Center, content = content)
    }
}

/**
 * Sleek compact pill badge for status, quantities, chips, and counters.
 */
@Composable
fun NeuPillBadge(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = NeuAccentBlue,
    backgroundColor: Color = NeuInsetBg,
    fontSize: androidx.compose.ui.unit.TextUnit = 10.sp
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = backgroundColor,
        border = BorderStroke(0.6.dp, NeuLightHighlight)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
