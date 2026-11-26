package io.github.dumahuch.pogodaapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.ui.pogodaText

/** Заливка плашки уровня: оттенок ступени едва проступает сквозь стекло, как у плашек предупреждений. */
internal const val LEVEL_PILL_FILL = 0.32f

/** Рамка плашки уровня. */
internal const val LEVEL_PILL_LINE = 0.55f

private val LevelPillShape = RoundedCornerShape(10.dp)

/**
 * Слово ступени (УФ, воздух, ветер, влажность) на плашке: цвет ступени в заливке и тонкой рамке, слово всегда цветом
 * текста (Дима 26.11.2026: цветной текст на меняющемся небе не читается). Плашка занимает столько, сколько нужно слову;
 * [modifier] задаёт место в строке (например, вес), выравнивание по левому краю и центру по высоте.
 */
@Composable
internal fun LevelPill(text: String, tint: Color, modifier: Modifier = Modifier, size: Float = 14f) {
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Text(
            text,
            style = pogodaText(size, 600, lineHeight = 1.15f),
            maxLines = 2,
            modifier = Modifier
                .clip(LevelPillShape)
                .background(tint.copy(alpha = LEVEL_PILL_FILL), LevelPillShape)
                .border(1.dp, tint.copy(alpha = LEVEL_PILL_LINE), LevelPillShape)
                .padding(horizontal = 9.dp, vertical = 2.dp),
        )
    }
}
