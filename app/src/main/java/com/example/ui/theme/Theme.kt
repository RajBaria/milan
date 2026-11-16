package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.data.model.AppColorTheme

val MilanShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: AppColorTheme = AppColorTheme.PINK,
    content: @Composable () -> Unit
) {
    val p = paletteForTheme(colorTheme)
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = p.darkPrimary,
            onPrimary = p.darkOnPrimary,
            primaryContainer = p.darkPrimaryContainer,
            onPrimaryContainer = p.darkOnPrimaryContainer,
            secondary = p.darkSecondary,
            onSecondary = p.darkOnSecondary,
            secondaryContainer = p.darkSecondaryContainer,
            onSecondaryContainer = p.darkOnSecondaryContainer,
            tertiary = p.darkTertiary,
            onTertiary = p.darkOnTertiary,
            background = p.darkBackground,
            onBackground = p.darkOnSurface,
            surface = p.darkSurface,
            onSurface = p.darkOnSurface,
            surfaceVariant = p.darkSurfaceVariant,
            onSurfaceVariant = p.darkOnSurfaceVariant,
            outline = p.darkOutline
        )
    } else {
        lightColorScheme(
            primary = p.lightPrimary,
            onPrimary = p.lightOnPrimary,
            primaryContainer = p.lightPrimaryContainer,
            onPrimaryContainer = p.lightOnPrimaryContainer,
            secondary = p.lightSecondary,
            onSecondary = p.lightOnSecondary,
            secondaryContainer = p.lightSecondaryContainer,
            onSecondaryContainer = p.lightOnSecondaryContainer,
            tertiary = p.lightTertiary,
            onTertiary = p.lightOnTertiary,
            tertiaryContainer = p.lightTertiaryContainer,
            onTertiaryContainer = p.lightOnTertiaryContainer,
            background = p.lightBackground,
            onBackground = p.lightOnSurface,
            surface = p.lightSurface,
            onSurface = p.lightOnSurface,
            surfaceVariant = p.lightSurfaceVariant,
            onSurfaceVariant = p.lightOnSurfaceVariant,
            outline = p.lightOutline
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = MilanShapes,
        content = content
    )
}
