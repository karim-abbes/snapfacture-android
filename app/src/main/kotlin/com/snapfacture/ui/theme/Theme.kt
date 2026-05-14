package com.snapfacture.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val Light = lightColorScheme(
    primary = BrandBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = BrandBlueDark,
    onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
    secondary = BrandAmber,
    onSecondary = androidx.compose.ui.graphics.Color.Black,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = OnSurfaceLight,
)

private val Dark = darkColorScheme(
    primary = BrandBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = BrandAmber,
    onSecondary = androidx.compose.ui.graphics.Color.Black,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
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
