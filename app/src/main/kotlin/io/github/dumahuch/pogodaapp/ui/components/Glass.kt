package io.github.dumahuch.pogodaapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.ui.PogodaPalette

/**
 * Стекло карточек: заливка цвета неба, блик по верхней левой кромке и тонкая линия.
 * Плотность заливки считается под небо (см. пакет sky). Размытия фона здесь пока нет:
 * оно придёт в версии 0.2.1 вместе с остальным видом стекла (Haze).
 */
fun Modifier.glassCard(shape: Shape, palette: PogodaPalette): Modifier = this
    .background(palette.glass, shape)
    .background(
        Brush.linearGradient(0f to palette.sheen, 0.55f to Color.Transparent),
        shape,
    )
    .border(1.dp, palette.glassLine, shape)

/** Плотная подложка кнопок и нижней панели. */
fun Modifier.glassButton(shape: Shape, palette: PogodaPalette): Modifier = this
    .background(palette.buttonBackground, shape)
    .border(1.dp, palette.glassLine, shape)
