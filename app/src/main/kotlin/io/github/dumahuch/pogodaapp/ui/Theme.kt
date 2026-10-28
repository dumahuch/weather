package io.github.dumahuch.pogodaapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NightSky = Color(0xFF0E1226)
private val Moonlight = Color(0xFFE8ECFA)

private val PogodaColors = darkColorScheme(
    background = NightSky,
    onBackground = Moonlight,
    surface = NightSky,
    onSurface = Moonlight,
)

@Composable
fun PogodaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PogodaColors, content = content)
}
