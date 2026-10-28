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
import io.github.dumahuch.pogodaapp.ui.PogodaColors
import io.github.dumahuch.pogodaapp.ui.components.LineIcon
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.pogodaText
import kotlin.math.roundToInt

private const val DEGREE_SCALE = 0.44f
private const val DEGREE_GAP_EM = 0.04f
private const val DEGREE_TOP_EM = 0.08f
private const val DIGIT_HALF_HEIGHT = 0.36f

/**
 * Базовая линия строки высотой 0,9 em у Manrope (верх 1,066 em, низ 0,3 em):
 * (0,9 − 1,366) / 2 + 1,066 = 0,833 em от верха строки.
 */
private const val BASELINE_EM = 0.833f
private const val ICON_SIZE_DP = 112
private const val ROW_MIN_HEIGHT_DP = 112

private fun Placeable.baselineOrDefault(): Int {
    val measured = this[FirstBaseline]
    return if (measured == AlignmentLine.Unspecified) (height * 0.8f).roundToInt() else measured
}

/**
 * Крупная температура и значок погоды (как в макете):
 * число с градусом по центру левой половины экрана, значок по центру правой,
 * центр значка на уровне середины цифр (без градуса). Высота блока не меньше 112 dp,
 * чтобы строка ниже не прыгала при смене размера цифр.
 */
@Composable
fun HeroRow(
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
                color = PogodaColors.Ink2,
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
        val half = width / 2
        val gap = (degreeSize * DEGREE_GAP_EM).sp.roundToPx()
        val blockWidth = digitsPlaceable.width + gap + degreePlaceable.width
        val blockX = (half - blockWidth) / 2

        // Положение считается от базовой линии, как в макете (строка высотой 0,9 em), а не от верха надписи:
        // так результат не зависит от того, как Compose распределяет межстрочный интервал.
        val digitsBaseline = digitsPlaceable.baselineOrDefault()
        val degreeBaseline = degreePlaceable.baselineOrDefault()
        val digitsBaselineWanted = (digitsSize * BASELINE_EM).sp.toPx()
        val digitsY = (digitsBaselineWanted - digitsBaseline).roundToInt()
        val degreeY = ((degreeSize * (DEGREE_TOP_EM + BASELINE_EM)).sp.toPx() - degreeBaseline).roundToInt()
        val digitsMiddle = digitsBaselineWanted - (digitsSize * DIGIT_HALF_HEIGHT).sp.toPx()
        val iconY = (digitsMiddle - iconPlaceable.height / 2f).roundToInt()

        val height = maxOf(ROW_MIN_HEIGHT_DP.dp.roundToPx(), iconY + iconPlaceable.height)
        layout(width, height) {
            digitsPlaceable.placeRelative(blockX, digitsY)
            degreePlaceable.placeRelative(blockX + digitsPlaceable.width + gap, degreeY)
            iconPlaceable.placeRelative(half + (half - iconPlaceable.width) / 2, iconY)
        }
    }
}
