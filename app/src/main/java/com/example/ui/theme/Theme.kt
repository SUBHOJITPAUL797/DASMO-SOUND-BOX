package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val LocalIsDarkTheme = staticCompositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Color(0xFF0A0D18),
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = SecondaryDark,
    onSecondary = Color(0xFF051912),
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = TertiaryDark,
    onTertiary = Color(0xFF1F0D02),
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = Color(0xFFFED7AA),
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    error = ErrorDark,
    onError = Color(0xFF2B070B),
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    outline = Outline,
    outlineVariant = OutlineVariant
)

val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

object AppSemanticColors {
    val emeraldText: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) SecondaryDark else BrandEmeraldDark

    val indigoText: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) PrimaryDark else BrandIndigo

    val orangeText: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) TertiaryDark else Color(0xFFEA580C)

    val amberText: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) Color(0xFFFBBF24) else Color(0xFFB45309)

    val coralText: Color
        @Composable @ReadOnlyComposable
        get() = if (LocalIsDarkTheme.current) ErrorDark else BrandCoral

    @Composable
    @ReadOnlyComposable
    fun adaptiveDepth(lightDepth: Color): Color =
        if (LocalIsDarkTheme.current) DepthDark else lightDepth
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
