package io.github.dumahuch.pogodaapp.ui.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Прозрачность тёмной фигуры выбранного часа: та же, что была у одной колонки. */
private const val BLOCK_ALPHA = 0.08f

private val TabInset = 2.dp
private val TabRadius = 14.dp
private val BodyRadius = 18.dp
private val Fillet = 14.dp

/** Насколько фигура уходит вниз за панель, в запас нижнего поля карточки. */
private val BodyOverhang = 4.dp

/** Геометрия ленты, нужная для фигуры: ширина колонки, высота ленты и отступ ленты от краёв карточки. */
internal class HourBlockSpec(val columnWidth: Dp, val stripHeight: Dp, val inset: Dp)

/**
 * Выбранный час и панель под лентой одной тёмной фигурой (Дима 26.11, вместо стрелки-«носика»): колонка часа сверху,
 * панель под ней, на стыке плавные внутренние углы. Фигура идёт за лентой при прокрутке; пока колонка не целиком в
 * видимой части ленты, рисуется простым объединением колонки и панели. [index] колонка часа, [scroll] сдвиг ленты
 * в пикселях, [alpha] появление и исчезновение (панель плавно раскрывается и сворачивается).
 */
internal fun Modifier.hourBlock(
    ink: Color,
    spec: HourBlockSpec,
    index: () -> Int,
    scroll: () -> Int,
    alpha: () -> Float,
): Modifier = drawBehind {
    val a = alpha()
    if (a <= 0f) return@drawBehind
    val color = ink.copy(alpha = BLOCK_ALPHA * a)
    val column = spec.columnWidth.toPx()
    val inset = spec.inset.toPx()
    val left = inset + column * index() - scroll()
    val tabLeft = left + TabInset.toPx()
    val tabRight = left + column - TabInset.toPx()
    val tabRadius = TabRadius.toPx()
    val strip = spec.stripHeight.toPx()
    val bodyHeight = size.height - strip + BodyOverhang.toPx()
    if (bodyHeight <= 1f) {
        drawRoundRect(color, Offset(tabLeft, 0f), Size(tabRight - tabLeft, strip), CornerRadius(tabRadius))
        return@drawBehind
    }
    val bodyLeft = inset
    val bodyRight = size.width - inset
    val bottom = strip + bodyHeight
    val radius = minOf(BodyRadius.toPx(), bodyHeight / 2)
    val fillet = minOf(Fillet.toPx(), bodyHeight / 2)
    val inside = tabLeft - fillet >= bodyLeft + radius && tabRight + fillet <= bodyRight - radius
    val shape = Path()
    if (inside) {
        shape.apply {
            moveTo(tabLeft, tabRadius)
            quadraticTo(tabLeft, 0f, tabLeft + tabRadius, 0f)
            lineTo(tabRight - tabRadius, 0f)
            quadraticTo(tabRight, 0f, tabRight, tabRadius)
            lineTo(tabRight, strip - fillet)
            quadraticTo(tabRight, strip, tabRight + fillet, strip)
            lineTo(bodyRight - radius, strip)
            quadraticTo(bodyRight, strip, bodyRight, strip + radius)
            lineTo(bodyRight, bottom - radius)
            quadraticTo(bodyRight, bottom, bodyRight - radius, bottom)
            lineTo(bodyLeft + radius, bottom)
            quadraticTo(bodyLeft, bottom, bodyLeft, bottom - radius)
            lineTo(bodyLeft, strip + radius)
            quadraticTo(bodyLeft, strip, bodyLeft + fillet, strip)
            lineTo(tabLeft - fillet, strip)
            quadraticTo(tabLeft, strip, tabLeft, strip - fillet)
            close()
        }
    } else {
        val body = Path().apply {
            addRoundRect(RoundRect(bodyLeft, strip, bodyRight, bottom, CornerRadius(radius)))
        }
        val tab = Path().apply {
            addRoundRect(RoundRect(tabLeft, 0f, tabRight, strip + radius, CornerRadius(tabRadius)))
        }
        shape.op(tab, body, PathOperation.Union)
    }
    drawPath(shape, color)
}
