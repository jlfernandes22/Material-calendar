package com.example.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.glance.color.ColorProviders
import androidx.glance.color.colorProviders
import androidx.glance.unit.ColorProvider

/**
 * Builds the widget palette from the app's Material 3 / Material You (dynamic)
 * color scheme, so widgets match the user's wallpaper like the app does.
 */
internal fun widgetColors(context: Context): ColorProviders =
    colorSchemeFor(context).toProviders()

private fun isNight(context: Context): Boolean {
    val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    return mode == Configuration.UI_MODE_NIGHT_YES
}

private fun colorSchemeFor(context: Context): ColorScheme {
    val dark = isNight(context)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
}

private fun ColorScheme.toProviders(): ColorProviders = colorProviders(
    primary = ColorProvider(primary),
    onPrimary = ColorProvider(onPrimary),
    primaryContainer = ColorProvider(primaryContainer),
    onPrimaryContainer = ColorProvider(onPrimaryContainer),
    secondary = ColorProvider(secondary),
    onSecondary = ColorProvider(onSecondary),
    secondaryContainer = ColorProvider(secondaryContainer),
    onSecondaryContainer = ColorProvider(onSecondaryContainer),
    tertiary = ColorProvider(tertiary),
    onTertiary = ColorProvider(onTertiary),
    tertiaryContainer = ColorProvider(tertiaryContainer),
    onTertiaryContainer = ColorProvider(onTertiaryContainer),
    error = ColorProvider(error),
    onError = ColorProvider(onError),
    errorContainer = ColorProvider(errorContainer),
    onErrorContainer = ColorProvider(onErrorContainer),
    background = ColorProvider(background),
    onBackground = ColorProvider(onBackground),
    surface = ColorProvider(surface),
    onSurface = ColorProvider(onSurface),
    surfaceVariant = ColorProvider(surfaceVariant),
    onSurfaceVariant = ColorProvider(onSurfaceVariant),
    outline = ColorProvider(outline),
    inverseOnSurface = ColorProvider(inverseOnSurface),
    inverseSurface = ColorProvider(inverseSurface),
    inversePrimary = ColorProvider(inversePrimary),
    widgetBackground = ColorProvider(surface)
)
