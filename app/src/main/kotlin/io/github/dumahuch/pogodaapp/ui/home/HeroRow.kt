package io.github.dumahuch.pogodaapp.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
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
private const val ICON_SIZE_DP = 112
private const val ROW_MIN_HEIGHT_DP = 112

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

        val measuredBaseline = digitsPlaceable[FirstBaseline]
        val baseline = if (measuredBaseline == AlignmentLine.Unspecified) {
            (digitsPlaceable.height * 0.8f).roundToInt()
        } else {
            measuredBaseline
        }
        val digitsMiddle = baseline - (digitsSize * DIGIT_HALF_HEIGHT).sp.toPx()
        val iconY = (digitsMiddle - iconPlaceable.height / 2f).roundToInt().coerceAtLeast(0)

        val height = maxOf(ROW_MIN_HEIGHT_DP.dp.roundToPx(), digitsPlaceable.height, iconY + iconPlaceable.height)
        layout(width, height) {
            digitsPlaceable.placeRelative(blockX, 0)
            degreePlaceable.placeRelative(
                blockX + digitsPlaceable.width + gap,
                (degreeSize * DEGREE_TOP_EM).sp.roundToPx(),
            )
            iconPlaceable.placeRelative(half + (half - iconPlaceable.width) / 2, iconY)
        }
    }
}
