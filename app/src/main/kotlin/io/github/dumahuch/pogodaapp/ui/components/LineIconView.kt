package io.github.dumahuch.pogodaapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import io.github.dumahuch.pogodaapp.weather.Condition

/**
 * Рисует линейный значок 24×24 с заданной толщиной линии (в единицах значка).
 * Значок главного экрана рисуется тонкой линией (0,85), мелкие 1,6, как в макете.
 */
@Composable
fun LineIconView(
    icon: LineIcon,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 1.6f,
) {
    val paths = remember(icon) { icon.paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        val factor = this.size.minDimension / ICON_VIEWPORT
        withTransform({ scale(factor, factor, pivot = Offset.Zero) }) {
            for (path in paths) {
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

private const val ICON_VIEWPORT = 24f

/** Значок вида погоды; ночью ясно и переменная облачность рисуются с луной. */
fun iconFor(condition: Condition, isDay: Boolean): LineIcon = when (condition) {
    Condition.Clear -> if (isDay) LineIcons.Sun else LineIcons.Moon
    Condition.Partly -> if (isDay) LineIcons.PartlyCloudy else LineIcons.PartlyCloudyNight
    Condition.Cloudy -> LineIcons.Cloud
    Condition.Rain -> LineIcons.Rain
    Condition.Storm -> LineIcons.Storm
    Condition.Snow -> LineIcons.Snow
    Condition.Fog -> LineIcons.Fog
}
