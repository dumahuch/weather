package io.github.dumahuch.pogodaapp.ui.map

import androidx.compose.ui.graphics.Color
import io.github.dumahuch.pogodaapp.ui.PogodaColors
import io.github.dumahuch.pogodaapp.ui.PogodaPalette

/**
 * Цвета интерфейса на карте. Подложка тёмная всегда (стиль basemap.de «Nacht»), поэтому палитра не берётся у неба:
 * светлый текст, тёмное стекло без размытия (размывать под стеклом нечего, карта рисуется отдельной поверхностью).
 * Мелкий текст Ink2 на стекле даёт контраст больше 7 : 1.
 */
val MapPalette = PogodaPalette(
    ink = Color(0xFFF4F6F8),
    ink2 = Color(0xFFB7BDC3),
    glass = Color(0xE6282A2D),
    glassAlert = Color(0xE6282A2D),
    glassLine = Color(0x26FFFFFF),
    glassHighlight = Color(0x1AFFFFFF),
    buttonHighlight = Color(0x22FFFFFF),
    shadow = Color(0x66000000),
    sheen = Color(0x0DFFFFFF),
    buttonBackground = Color(0xE62B2D30),
    buttonOn = Color(0x2EFFFFFF),
    button2 = Color(0x1AFFFFFF),
    accent = Color(0xFF6FB6FF),
    accentSoft = Color(0x4D3D8BFF),
    bar = Color(0xFF6FB6FF),
    warning = PogodaColors.Warning,
    scrim = Color.Transparent,
    infoScrim = Color.Transparent,
    infoBackdrop = Color(0xFF2B2C2D),
    darkInk = false,
    smoke = 1f,
)

/** Цвет под картой, пока подложка не загрузилась: как суша в тёмном стиле basemap.de. */
val MapBackground = Color(0xFF191A1A)
