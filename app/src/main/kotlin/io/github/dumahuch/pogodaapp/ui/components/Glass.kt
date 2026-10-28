package io.github.dumahuch.pogodaapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.ui.PogodaColors

/**
 * Матовое стекло карточек. Размытия фона здесь пока нет: фон спокойный и однотонный,
 * размытие (Haze) появится вместе с картинами неба.
 */
fun Modifier.glassCard(shape: Shape): Modifier = this
    .background(PogodaColors.Glass, shape)
    .background(
        Brush.linearGradient(0f to PogodaColors.Sheen, 0.55f to Color.Transparent),
        shape,
    )
    .border(1.dp, PogodaColors.GlassLine, shape)

/** Плотная светлая подложка кнопок и нижней панели. */
fun Modifier.glassButton(shape: Shape): Modifier = this
    .background(PogodaColors.ButtonBackground, shape)
    .border(1.dp, PogodaColors.GlassLine, shape)
