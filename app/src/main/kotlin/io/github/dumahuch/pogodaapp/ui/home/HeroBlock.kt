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
import io.github.dumahuch.pogodaapp.home.GLYPH_LIFT
import io.github.dumahuch.pogodaapp.home.HeroIcon
import io.github.dumahuch.pogodaapp.home.HeroWidth
import io.github.dumahuch.pogodaapp.home.glyphCloudBottom
import io.github.dumahuch.pogodaapp.settings.WeatherIconStyle
import io.github.dumahuch.pogodaapp.ui.HeroLetterSpacing
import io.github.dumahuch.pogodaapp.ui.pogodaText
import kotlin.math.roundToInt

/**
 * Верх градуса вровень с верхом цифр: у Manrope верх цифр 0,72 em, верх знака градуса 0,735 em его размера, базовая
 * линия строки [BASELINE_EM]. Отступ базовой линии градуса от верха строки в долях его размера.
 */
private val DEGREE_TOP_EM = (BASELINE_EM - 0.72f) / HeroWidth.DEGREE_SCALE + 0.735f - BASELINE_EM

/**
 * Базовая линия строки высотой 0,9 em у Manrope (верх 1,066 em, низ 0,3 em):
 * (0,9 − 1,366) / 2 + 1,066 = 0,833 em от верха строки.
 */
private const val BASELINE_EM = 0.833f

/**
 * Зазор над строкой цифр (step-40, решение Димы 07.11.2026). До этого здесь было 98 dp, место бывшего значка 96 dp над
 * цифрами (до step-27), и оно росло вместе с цифрами: при масштабе 1,35 около 132 dp пустоты. Зазор фиксированный и
 * с масштабом не растёт.
 */
private const val TOP_GAP_DP = 12

/**
 * Вес цифр и градуса: снова тонкие 200 (решение Димы 07.11.2026, step-42: 400 при цифрах до 1,3 читалась толстой).
 * Manrope переменный, вес любой от 200 до 800.
 */
private const val DIGITS_WEIGHT = 200

/** Наибольший размер цифры (sp): высота блока считается по нему, чтобы строки ниже не прыгали при смене числа. */
private const val MAX_DIGITS_SP = 124

private fun Placeable.baselineOrDefault(): Int {
    val measured = this[FirstBaseline]
    return if (measured == AlignmentLine.Unspecified) (height * 0.8f).roundToInt() else measured
}

/**
 * Текущая погода по центру: огромная температура, справа сверху градус, под градусом значок погоды (step-27). Нижний край
 * значка стоит на нижнем крае цифр. По центру экрана стоит весь блок целиком (цифры, зазор, градус или значок: step-28),
 * при ясной погоде это цифры с градусом. При ясной погоде значка нет ([icon] пустой): солнце и луна на небе.
 * Высота блока всегда считается по самому крупному размеру цифр ([MAX_DIGITS_SP]).
 *
 * [scale] увеличивает цифры и значок (с версии 0.6.0 блок растёт на свободное место, см.
 * [io.github.dumahuch.pogodaapp.home.HeroFit]); высота блока: зазор [TOP_GAP_DP] плюс [scale] × [heroDigitsHeight].
 */
@Composable
fun HeroBlock(
    digits: String,
    digitsSize: Int,
    description: String,
    icon: HeroIcon?,
    iconStyle: WeatherIconStyle,
    night: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val numberSize = digitsSize * scale
    val degreeSize = numberSize * HeroWidth.DEGREE_SCALE
    // Значок растёт вместе с цифрами (step-44, вариант D): 72 dp при цифрах 124 sp и масштабе 1, 94 dp при 1,3.
    val glyphSize = HeroWidth.glyphDp(digitsSize, scale).dp
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
                WeatherGlyph(icon, iconStyle, glyphSize, night, Modifier.clearAndSetSemantics { })
            }
        },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val digitsPlaceable = measurables[0].measure(loose)
        val degreePlaceable = measurables[1].measure(loose)
        val glyphPlaceable = measurables.getOrNull(2)?.measure(loose)

        val width = constraints.maxWidth
        val gap = (degreeSize * HeroWidth.DEGREE_GAP_EM).sp.roundToPx()
        // Правая колонка (градус и значок) шире из двух; по центру стоят цифры с градусом и половина ширины значка
        // (вариант D, [HeroWidth.centeringSide]): цифры ближе к центру экрана, лёгкий значок свисает вправо.
        val pull = (numberSize * HeroWidth.DEGREE_PULL_EM).sp.roundToPx()
        val degreeSide = degreePlaceable.width - pull
        val centering = HeroWidth.centeringSide(degreeSide.toFloat(), (glyphPlaceable?.width ?: 0).toFloat()).roundToInt()
        val digitsX = ((width - (digitsPlaceable.width + gap + centering)) / 2).coerceAtLeast(0)
        val sideX = digitsX + digitsPlaceable.width + gap
        val lineTop = TOP_GAP_DP.dp.roundToPx()

        // Положение цифр считается от базовой линии, как в макете (строка высотой 0,9 em), а не от верха надписи:
        // так результат не зависит от того, как Compose распределяет межстрочный интервал.
        val digitsBaseline = digitsPlaceable.baselineOrDefault()
        val degreeBaseline = degreePlaceable.baselineOrDefault()
        val digitsBaselineWanted = (numberSize * BASELINE_EM).sp.toPx()
        val digitsY = lineTop + (digitsBaselineWanted - digitsBaseline).roundToInt()
        val degreeY = lineTop + ((degreeSize * (DEGREE_TOP_EM + BASELINE_EM)).sp.toPx() - degreeBaseline).roundToInt()
        // У цифр нет нижних выносных: нижний край цифр это базовая линия. Нижний край облака любого значка стоит над ней
        // на одной и той же высоте (GLYPH_LIFT), всё остальное (штрихи дождя, молния, снег) свисает ниже облака.
        val glyphY = if (icon == null) 0 else {
            val unit = glyphSize.toPx() / 24f
            (lineTop + digitsBaselineWanted - glyphCloudBottom(icon) * unit - GLYPH_LIFT * unit)
                .roundToInt()
        }

        val height = lineTop + (MAX_DIGITS_SP * scale * LINE_HEIGHT_EM).sp.roundToPx()
        layout(width, height) {
            digitsPlaceable.placeRelative(digitsX, digitsY)
            degreePlaceable.placeRelative(sideX - pull, degreeY)
            glyphPlaceable?.placeRelative(sideX, glyphY)
        }
    }
}

private const val LINE_HEIGHT_EM = 0.9f

/** Высота блока при обычном размере (зазор сверху и строка цифр). */
internal fun Density.heroBaseHeight(): Float = TOP_GAP_DP.dp.toPx() + heroDigitsHeight()

/** Высота строки цифр при обычном размере: это та часть блока, которая растёт с масштабом ([HeroBlock], scale). */
internal fun Density.heroDigitsHeight(): Float = (MAX_DIGITS_SP * LINE_HEIGHT_EM).sp.toPx()
