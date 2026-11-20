package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ChessDarkColorScheme = darkColorScheme(
    primary = TournamentEmerald,
    onPrimary = Color(0xFF0E1A07),
    primaryContainer = TournamentEmeraldContainer,
    onPrimaryContainer = Color(0xFFC5F09B),
    secondary = GoldAccent,
    onSecondary = Color(0xFF261C04),
    secondaryContainer = GoldContainer,
    onSecondaryContainer = Color(0xFFFFE094),
    tertiary = CyanAccent,
    onTertiary = Color(0xFF03211F),
    background = ObsidianBg,
    onBackground = CreamText,
    surface = ObsidianSurface,
    onSurface = CreamText,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = MutedStoneText,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceHigh,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF2E2C28),
    error = ClassBlunder,
    onError = Color.White,
    errorContainer = ResultLossBg,
    onErrorContainer = ResultLossFg
)

private val ChessLightColorScheme = lightColorScheme(
    primary = TournamentEmeraldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F0C2),
    onPrimaryContainer = Color(0xFF1A3008),
    secondary = Color(0xFF9C7212),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCE9BC),
    onSecondaryContainer = Color(0xFF332200),
    tertiary = Color(0xFF0D7D78),
    onTertiary = Color.White,
    background = LightParchmentBg,
    onBackground = LightInkText,
    surface = LightCardSurface,
    onSurface = LightInkText,
    surfaceVariant = LightCardHigh,
    onSurfaceVariant = LightMutedText,
    outline = Color(0xFFD5D0C5),
    error = ClassBlunder,
    onError = Color.White
)

private val ChessAnalystShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun ChessAnalystTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ChessDarkColorScheme else ChessLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = ChessAnalystTypography,
        shapes = ChessAnalystShapes,
        content = content
    )
}
