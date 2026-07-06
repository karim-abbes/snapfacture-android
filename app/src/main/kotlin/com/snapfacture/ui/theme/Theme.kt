package com.snapfacture.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Both schemes define every container slot the screens actually use
// (primary/secondary/tertiary containers); missing slots would silently
// fall back to the purple Material baseline.
private val Light = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueDark,
    onPrimaryContainer = Color.White,
    secondary = BrandAmber,
    onSecondary = Color.Black,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = OnAmberContainerLight,
    tertiaryContainer = BlueContainerLight,
    onTertiaryContainer = OnBlueContainerLight,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = Color.White,
    onSurface = OnSurfaceLight,
)

private val Dark = darkColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueDark,
    onPrimaryContainer = Color.White,
    secondary = BrandAmber,
    onSecondary = Color.Black,
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = OnAmberContainerDark,
    tertiaryContainer = BlueContainerDark,
    onTertiaryContainer = OnBlueContainerDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceElevatedDark,
    onSurface = OnSurfaceDark,
)

@Composable
fun SnapfactureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = SnapfactureTypography,
        content = content
    )
}
