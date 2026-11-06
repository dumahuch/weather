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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dumahuch.pogodaapp.home.HeroIcon
import io.github.dumahuch.pogodaapp.settings.WeatherIconStyle
import io.github.dumahuch.pogodaapp.ui.HeroLetterSpacing
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

/**
 * Место над цифрами, где до step-27 стоял значок 96 dp (вместе с промежутком 2 dp). Оставлено пустым: цифры и строки под
 * ними стоят там же, где раньше, подложки под небо (вуаль 96–440 dp, строки под температурой) рассчитаны на это место.
 * Над цифрами теперь видно небо с солнцем или луной.
 */
private const val TOP_ROOM_DP = 98

/** Значок погоды справа от цифр (решение Димы 06.11.2026): 52 dp при цифрах 124 sp, у мелких цифр меньше в той же доле. */
private const val GLYPH_DP = 52f

/** Цифры и градус плотнее прежних тонких 200 (просьба Димы 06.11.2026); Manrope переменный, вес любой от 200 до 800. */
private const val DIGITS_WEIGHT = 400

/** Наибольший размер цифры (sp): высота блока считается по нему, чтобы строки ниже не прыгали при смене числа. */
private const val MAX_DIGITS_SP = 124

private fun Placeable.baselineOrDefault(): Int {
    val measured = this[FirstBaseline]
    return if (measured == AlignmentLine.Unspecified) (height * 0.8f).roundToInt() else measured
}

/**
 * Текущая погода по центру: огромная температура, справа сверху градус, под градусом значок погоды (step-27). Нижний край
 * значка стоит на нижнем крае цифр. По центру экрана стоят сами цифры, градус и значок висят справа, поэтому число не
 * сдвигается, когда значок появляется или пропадает. При ясной погоде значка нет ([icon] пустой): солнце и луна на небе.
 * Высота блока всегда считается по самому крупному размеру цифр ([MAX_DIGITS_SP]).
 *
 * [scale] увеличивает цифры и значок вместе (с версии 0.6.0 блок растёт на свободное место, см.
 * [io.github.dumahuch.pogodaapp.home.HeroFit]); высота блока ровно [scale] × [heroBaseHeight].
 */
@Composable
fun HeroBlock(
    digits: String,
    digitsSize: Int,
    description: String,
    icon: HeroIcon?,
    iconStyle: WeatherIconStyle,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val numberSize = digitsSize * scale
    val degreeSize = numberSize * DEGREE_SCALE
    val glyphSize = (GLYPH_DP * scale * digitsSize / MAX_DIGITS_SP).dp
    Layout(
        content = {
            Text(
                text = digits,
                style = pogodaText(
                    size = numberSize,
                    weight = DIGITS_WEIGHT,
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
                style = pogodaText(size = degreeSize, weight = DIGITS_WEIGHT, lineHeight = 0.9f),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clearAndSetSemantics { },
            )
            if (icon != null) {
                WeatherGlyph(icon, iconStyle, glyphSize, Modifier.clearAndSetSemantics { })
            }
        },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val digitsPlaceable = measurables[0].measure(loose)
        val degreePlaceable = measurables[1].measure(loose)
        val glyphPlaceable = measurables.getOrNull(2)?.measure(loose)

        val width = constraints.maxWidth
        val gap = (degreeSize * DEGREE_GAP_EM).sp.roundToPx()
        val digitsX = (width - digitsPlaceable.width) / 2
        val sideX = digitsX + digitsPlaceable.width + gap
        val lineTop = (TOP_ROOM_DP * scale).dp.roundToPx()

        // Положение цифр считается от базовой линии, как в макете (строка высотой 0,9 em), а не от верха надписи:
        // так результат не зависит от того, как Compose распределяет межстрочный интервал.
        val digitsBaseline = digitsPlaceable.baselineOrDefault()
        val degreeBaseline = degreePlaceable.baselineOrDefault()
        val digitsBaselineWanted = (numberSize * BASELINE_EM).sp.toPx()
        val digitsY = lineTop + (digitsBaselineWanted - digitsBaseline).roundToInt()
        val degreeY = lineTop + ((degreeSize * (DEGREE_TOP_EM + BASELINE_EM)).sp.toPx() - degreeBaseline).roundToInt()
        // У цифр нет нижних выносных: нижний край цифр это базовая линия. На неё ставится нижний край рисунка значка.
        val glyphY = if (icon == null) 0 else {
            val bottom = glyphBottom(icon, iconStyle, glyphSize) / 24f * glyphSize.toPx()
            (lineTop + digitsBaselineWanted - bottom).roundToInt()
        }

        val height = lineTop + (MAX_DIGITS_SP * scale * LINE_HEIGHT_EM).sp.roundToPx()
        layout(width, height) {
            digitsPlaceable.placeRelative(digitsX, digitsY)
            degreePlaceable.placeRelative(sideX, degreeY)
            glyphPlaceable?.placeRelative(sideX, glyphY)
        }
    }
}

private const val LINE_HEIGHT_EM = 0.9f

/** Высота блока при обычном размере (место сверху и строка цифр): от неё считается рост ([HeroBlock], scale). */
internal fun Density.heroBaseHeight(): Float =
    TOP_ROOM_DP.dp.toPx() + (MAX_DIGITS_SP * LINE_HEIGHT_EM).sp.toPx()
