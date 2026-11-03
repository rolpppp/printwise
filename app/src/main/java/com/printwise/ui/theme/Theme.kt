package com.printwise.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tokens Material's [ColorScheme] has no slot for. The editing workspace and print preview use these so the
 * user's artwork, not the brand palette, stays the centrepiece whichever theme is active.
 */
@Immutable
data class PrintwiseColors(
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val textDisabled: Color,
    val primaryPressed: Color,
    val secondaryPressed: Color,
    /** Around the sheet in the editor. */
    val workspace: Color,
    /** The sheet itself and every printed page: always white, never themed. */
    val paper: Color,
    val unselectedBorder: Color,
    val guide: Color,
    val grid: Color
)

/** One complete visual identity. Components read tokens only, so a second theme is just another instance. */
@Immutable
data class PrintwiseThemeSpec(val name: String, val scheme: ColorScheme, val colors: PrintwiseColors)

private val Ink = Color(0xFF17233B)

// Theme A, "Creative Precision". Spec values are adjusted only where WCAG AA needs it: secondary text (spec
// #718096 is 3.8:1 on the background), error text (spec #E05252 is 3.8:1) and outlines (3:1 for component edges).
val CreativePrecision = PrintwiseThemeSpec(
    name = "Creative Precision",
    scheme = lightColorScheme(
        primary = Color(0xFF3563F5),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE4EBFF),
        onPrimaryContainer = Color(0xFF112A73),
        inversePrimary = Color(0xFFA9BDFF),
        secondary = Color(0xFF20C9B0),
        onSecondary = Ink,
        secondaryContainer = Color(0xFFD3F5EF),
        onSecondaryContainer = Color(0xFF0B4A41),
        tertiary = Color(0xFFFF914D),
        onTertiary = Ink,
        tertiaryContainer = Color(0xFFFFE7D6),
        onTertiaryContainer = Color(0xFF6E2E00),
        background = Color(0xFFF8FAFC),
        onBackground = Ink,
        surface = Color.White,
        onSurface = Ink,
        surfaceVariant = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFF5A6B82),
        surfaceTint = Color.White,
        surfaceBright = Color.White,
        surfaceDim = Color(0xFFE2E8F0),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color.White,
        surfaceContainer = Color.White,
        surfaceContainerHigh = Color.White,
        surfaceContainerHighest = Color(0xFFF1F5F9),
        inverseSurface = Ink,
        inverseOnSurface = Color(0xFFF1F5F9),
        error = Color(0xFFC93C3C),
        onError = Color.White,
        errorContainer = Color(0xFFFDECEC),
        onErrorContainer = Color(0xFF7A1F1F),
        outline = Color(0xFF7D8CA1),
        outlineVariant = Color(0xFFE2E8F0),
        scrim = Color.Black
    ),
    colors = PrintwiseColors(
        accent = Color(0xFFFF914D),
        onAccent = Ink,
        success = Color(0xFF16A36A),
        warning = Color(0xFFE9A23B),
        info = Color(0xFF3563F5),
        textDisabled = Color(0xFFA0AEC0),
        primaryPressed = Color(0xFF2851DB),
        secondaryPressed = Color(0xFF16AD99),
        workspace = Color(0xFFF1F3F5),
        paper = Color.White,
        unselectedBorder = Color(0xFFCBD5E1),
        guide = Color(0xFF64748B),
        grid = Color(0xFFE2E8F0)
    )
)

private val LocalPrintwiseColors = staticCompositionLocalOf { CreativePrecision.colors }

/** Printwise-specific tokens, read alongside the Material ones: `MaterialTheme.printwise.workspace`. */
val MaterialTheme.printwise: PrintwiseColors
    @Composable @ReadOnlyComposable get() = LocalPrintwiseColors.current

/** Shared timing so every transition in the app moves the same way. */
object Motion {
    /** Strong ease-out: quick to respond, gentle to settle. */
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    const val FAST_MS = 150
    const val BASE_MS = 200
}

private val AppTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

// extraSmall: paper corners · small: tiles and fields · medium: buttons and controls · large: cards.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PrintwiseTheme(spec: PrintwiseThemeSpec = CreativePrecision, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPrintwiseColors provides spec.colors) {
        MaterialTheme(
            colorScheme = spec.scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
