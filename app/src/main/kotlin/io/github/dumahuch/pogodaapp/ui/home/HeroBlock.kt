package io.github.dumahuch.pogodaapp.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dumahuch.pogodaapp.ui.HeroLetterSpacing
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LineIcon
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.pogodaText
import kotlin.math.roundToInt

private const val DEGREE_SCALE = 0.44f
private const val DEGREE_GAP_EM = 0.04f
private const val DEGREE_TOP_EM = 0.08f

/**
 * Базовая линия строки высотой 0,9 em у Manrope (верх 1,066 em, низ 0,3 em):
 * (0,9 − 1,366) / 2 + 1,066 = 0,833 em от верха строки.
 */
private const val BASELINE_EM = 0.833f
private const val ICON_SIZE_DP = 96
private const val ICON_GAP_DP = 2

/** Наибольший размер цифры (sp): высота блока считается по нему, чтобы строки ниже не прыгали при смене числа. */
private const val MAX_DIGITS_SP = 124

private fun Placeable.baselineOrDefault(): Int {
    val measured = this[FirstBaseline]
    return if (measured == AlignmentLine.Unspecified) (height * 0.8f).roundToInt() else measured
}

/**
 * Текущая погода по центру (вариант «В»): сверху крупный значок погоды, под ним огромная тонкая температура с градусом.
 * Число с градусом стоит по центру экрана (блок «цифры + градус» центрируется целиком, как в макете). Высота блока
 * всегда считается по самому крупному размеру цифр ([MAX_DIGITS_SP]).
 */
@Composable
fun HeroBlock(
    digits: String,
    digitsSize: Int,
    description: String,
    icon: LineIcon,
    modifier: Modifier = Modifier,
) {
    val degreeSize = digitsSize * DEGREE_SCALE
    Layout(
        content = {
            Text(
                text = digits,
                style = pogodaText(
                    size = digitsSize.toFloat(),
                    weight = 200,
                    lineHeight = 0.9f,
                    letterSpacing = HeroLetterSpacing,
                    tabular = true,
                ),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.semantics { contentDescription = description },
            )
            Text(
                text = "°",
                style = pogodaText(size = degreeSize, weight = 200, lineHeight = 0.9f),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clearAndSetSemantics { },
            )
            LineIconView(
                icon = icon,
                size = ICON_SIZE_DP.dp,
                color = LocalPogodaPalette.current.ink2,
                strokeWidth = 0.85f,
                modifier = Modifier.clearAndSetSemantics { },
            )
        },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val digitsPlaceable = measurables[0].measure(loose)
        val degreePlaceable = measurables[1].measure(loose)
        val iconPlaceable = measurables[2].measure(loose)

        val width = constraints.maxWidth
        val gap = (degreeSize * DEGREE_GAP_EM).sp.roundToPx()
        val blockWidth = digitsPlaceable.width + gap + degreePlaceable.width
        val blockX = (width - blockWidth) / 2
        val iconX = (width - iconPlaceable.width) / 2
        val lineTop = iconPlaceable.height + ICON_GAP_DP.dp.roundToPx()

        // Положение цифр считается от базовой линии, как в макете (строка высотой 0,9 em), а не от верха надписи:
        // так результат не зависит от того, как Compose распределяет межстрочный интервал.
        val digitsBaseline = digitsPlaceable.baselineOrDefault()
        val degreeBaseline = degreePlaceable.baselineOrDefault()
        val digitsBaselineWanted = (digitsSize * BASELINE_EM).sp.toPx()
        val digitsY = lineTop + (digitsBaselineWanted - digitsBaseline).roundToInt()
        val degreeY = lineTop + ((degreeSize * (DEGREE_TOP_EM + BASELINE_EM)).sp.toPx() - degreeBaseline).roundToInt()

        val height = lineTop + (MAX_DIGITS_SP * LINE_HEIGHT_EM).sp.roundToPx()
        layout(width, height) {
            iconPlaceable.placeRelative(iconX, 0)
            digitsPlaceable.placeRelative(blockX, digitsY)
            degreePlaceable.placeRelative(blockX + digitsPlaceable.width + gap, degreeY)
        }
    }
}

private const val LINE_HEIGHT_EM = 0.9f
