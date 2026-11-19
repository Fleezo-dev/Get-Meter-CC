package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.data.preferences.SettingsRepository

@Immutable
data class GetTaxiColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val primaryAccent: Color,
    val onPrimaryAccent: Color,
    val yellowHighlight: Color,
    val yellowContainer: Color,
    val yellowText: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val greenSuccess: Color,
    val redDanger: Color,
    val meterDisplayBg: Color,
    val meterDisplayBorder: Color
)

val NightGetTaxiColors = GetTaxiColors(
    isDark = true,
    background = TaxiBlack,
    surface = TaxiCharcoal,
    surfaceElevated = TaxiCharcoalSurface,
    border = TaxiCharcoalBorder,
    primaryAccent = TaxiYellow,
    onPrimaryAccent = TaxiBlack,
    yellowHighlight = TaxiYellowBright,
    yellowContainer = Color(0x26FFC107),
    yellowText = TaxiYellowBright,
    textPrimary = TaxiWhite,
    textSecondary = TaxiMutedText,
    greenSuccess = TaxiGreenLight,
    redDanger = TaxiRed,
    meterDisplayBg = Color(0xFF101318),
    meterDisplayBorder = Color(0xFF2B3240)
)

val DayGetTaxiColors = GetTaxiColors(
    isDark = false,
    background = DayBackground,
    surface = DaySurface,
    surfaceElevated = DaySurfaceElevated,
    border = DayBorder,
    primaryAccent = DayPrimaryRed,
    onPrimaryAccent = TaxiWhite,
    yellowHighlight = DayYellowHighlight,
    yellowContainer = DayYellowSoftContainer,
    yellowText = DayYellowOnLight,
    textPrimary = DayTextPrimary,
    textSecondary = DayTextSecondary,
    greenSuccess = TaxiGreen,
    redDanger = DayPrimaryRed,
    meterDisplayBg = Color(0xFFFFFFFF),
    meterDisplayBorder = Color(0xFFE2E8F0)
)

val LocalGetTaxiColors = staticCompositionLocalOf { NightGetTaxiColors }

private val TaxiDarkColorScheme = darkColorScheme(
    primary = TaxiYellow,
    onPrimary = TaxiBlack,
    primaryContainer = TaxiCharcoalElevated,
    onPrimaryContainer = TaxiYellowBright,
    secondary = TaxiWhite,
    onSecondary = TaxiBlack,
    secondaryContainer = TaxiCharcoalSurface,
    onSecondaryContainer = TaxiWhite,
    tertiary = TaxiYellowBright,
    onTertiary = TaxiBlack,
    background = TaxiBlack,
    onBackground = TaxiWhite,
    surface = TaxiCharcoal,
    onSurface = TaxiWhite,
    surfaceVariant = TaxiCharcoalSurface,
    onSurfaceVariant = TaxiMutedText,
    outline = TaxiCharcoalBorder,
    error = TaxiRed,
    onError = TaxiWhite
)

private val TaxiLightColorScheme = lightColorScheme(
    primary = DayPrimaryRed,
    onPrimary = TaxiWhite,
    primaryContainer = DayPrimaryRedContainer,
    onPrimaryContainer = DayPrimaryRed,
    secondary = DayYellowHighlight,
    onSecondary = DayTextPrimary,
    secondaryContainer = DayYellowSoftContainer,
    onSecondaryContainer = DayYellowOnLight,
    tertiary = DayYellowHighlight,
    onTertiary = DayTextPrimary,
    background = DayBackground,
    onBackground = DayTextPrimary,
    surface = DaySurface,
    onSurface = DayTextPrimary,
    surfaceVariant = DaySurfaceElevated,
    onSurfaceVariant = DayTextSecondary,
    outline = DayBorder,
    error = DayPrimaryRed,
    onError = TaxiWhite
)

@Composable
fun MyApplicationTheme(
    appearanceMode: String = SettingsRepository.APPEARANCE_AUTO,
    darkTheme: Boolean? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = darkTheme ?: when (appearanceMode.uppercase()) {
        SettingsRepository.APPEARANCE_DAY -> false
        SettingsRepository.APPEARANCE_NIGHT -> true
        else -> systemDark
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (useDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDarkTheme -> TaxiDarkColorScheme
        else -> TaxiLightColorScheme
    }

    val getTaxiColors = if (useDarkTheme) NightGetTaxiColors else DayGetTaxiColors

    CompositionLocalProvider(LocalGetTaxiColors provides getTaxiColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
