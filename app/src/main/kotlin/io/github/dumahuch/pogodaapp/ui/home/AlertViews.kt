package io.github.dumahuch.pogodaapp.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.PogodaColors
import io.github.dumahuch.pogodaapp.ui.components.Glass
import io.github.dumahuch.pogodaapp.ui.components.LineIcon
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.glassButton
import io.github.dumahuch.pogodaapp.ui.components.glassWeatherInside
import io.github.dumahuch.pogodaapp.ui.components.glassWeatherOutside
import io.github.dumahuch.pogodaapp.ui.components.hazeCardBlur
import io.github.dumahuch.pogodaapp.ui.forecast.clockText
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.AlertKind
import io.github.dumahuch.pogodaapp.weather.AlertLevel
import io.github.dumahuch.pogodaapp.weather.AlertWhen
import io.github.dumahuch.pogodaapp.weather.WeatherAlert
import java.time.Instant
import java.time.ZoneId

/** Промежуток между плашками предупреждений, как в макете. */
internal val AlertGap = 8.dp

/** Наименьшая высота плашки предупреждения и строки «Ещё N» (плашка плотнее макета: 42 вместо 46 dp, «Ещё N» 38 dp). */
internal val AlertRowHeight = 42.dp
internal val AlertMoreHeight = 38.dp

private val AlertShape = RoundedCornerShape(16.dp)
private val MoreShape = RoundedCornerShape(14.dp)

/** Доля цвета уровня в заливке плашки и в её тонкой рамке (макет: 15 % и 26 %). */
internal const val TINT_FILL = 0.15f
internal const val TINT_LINE = 0.26f

/** Цвет уровня DWD (приглушённый под стиль приложения). */
internal fun levelColor(level: AlertLevel): Color = PogodaColors.AlertLevels[level.number - 1]

/** Значок на плашке: треугольник у уровней 1 и 2, восьмиугольник у 3 и 4 (как в макете). */
internal fun levelIcon(level: AlertLevel): LineIcon =
    if (level.number >= AlertLevel.Severe.number) LineIcons.Severe else LineIcons.Warning

/** У снега, льда и мороза и на плашке стоит снежинка: она одна во всех местах. */
internal fun AlertKind.isSnowflake(): Boolean = this == AlertKind.Snow || this == AlertKind.Frost || this == AlertKind.Ice

@Composable
internal fun alertTitle(kind: AlertKind): String = stringArrayResource(R.array.alert_kinds)[kind.ordinal]

@Composable
internal fun levelName(level: AlertLevel): String = stringArrayResource(R.array.alert_levels)[level.number - 1]

/** «Уровень 2 · Выраженная погода»: вторая строка плашки. */
@Composable
internal fun levelLine(level: AlertLevel): String = stringResource(R.string.alert_level_line, level.number, levelName(level))

/** Подпись справа на плашке: «через 20 мин», «с 15:00», «завтра с 06:00», «до 06:00», «до пт 06:00»; пусто, если конец не известен. */
@Composable
internal fun alertWhenText(alert: WeatherAlert, now: Instant, zone: ZoneId): String {
    val label = alertWhenLabel(alert, now, zone, stringArrayResource(R.array.days_short)) ?: return ""
    return stringResource(label.res, *label.args)
}

/** Подпись времени предупреждения как ресурс строки и её аргументы: одна и та же для плашки и для уведомления. */
internal class AlertWhenLabel(val res: Int, vararg val args: Any)

/** Ресурс и аргументы подписи [alertWhenText]; [days] короткие названия дней «Пн…Вс». `null`, если конец не известен. */
internal fun alertWhenLabel(alert: WeatherAlert, now: Instant, zone: ZoneId, days: Array<String>): AlertWhenLabel? {
    fun dayName(at: Instant) = days[at.atZone(zone).dayOfWeek.value - 1]
    return when (val moment = AlertWhen.of(alert, now, zone)) {
        is AlertWhen.StartsIn -> AlertWhenLabel(R.string.alert_starts_in, moment.minutes)
        is AlertWhen.From -> when (moment.days) {
            0 -> AlertWhenLabel(R.string.alert_from, clockText(moment.at, zone))
            1 -> AlertWhenLabel(R.string.alert_from_tomorrow, clockText(moment.at, zone))
            else -> AlertWhenLabel(R.string.alert_from_day, clockText(moment.at, zone), dayName(moment.at))
        }
        is AlertWhen.Until -> when (moment.days) {
            0 -> AlertWhenLabel(R.string.alert_until, clockText(moment.at, zone))
            1 -> AlertWhenLabel(R.string.alert_until_tomorrow, clockText(moment.at, zone))
            else -> AlertWhenLabel(R.string.alert_until_day, clockText(moment.at, zone), dayName(moment.at))
        }
        AlertWhen.Ongoing -> null
    }
}

/**
 * Стекло плашки предупреждения: плоское и прозрачное, без тени и светлой кромки (решение Димы), с размытым фоном,
 * заливкой стекла с долей цвета уровня и тонкой рамкой того же цвета. Цветной полоски у края нет.
 * Плотность заливки берётся наибольшая из рассчитанных для карточек и для предупреждений: плашки лежат между полосой
 * предупреждений макета и лентой «По часам», и текст должен читаться на обеих.
 */
private fun Modifier.alertGlass(shape: Shape, glass: Glass, tint: Color): Modifier {
    val palette = glass.palette
    val base = if (palette.glassAlert.alpha >= palette.glass.alpha) palette.glassAlert else palette.glass
    val fill = tint.copy(alpha = TINT_FILL).compositeOver(base)
    return this
        .glassWeatherOutside(shape, glass)
        .clip(shape)
        .border(1.dp, tint.copy(alpha = TINT_LINE), shape)
        .hazeCardBlur(glass)
        .background(fill, shape)
        .glassWeatherInside(glass)
}

/**
 * Плашка предупреждения: значок уровня, справа от него две строки и время. Первая строка: название явления; вторая:
 * уровень на всю ширину текста (не сокращается из-за времени). Нажатие раскрывает плашку на месте (Дима 26.11, вместо
 * шторки): под строкой шкала уровня, период, район, оригинальный текст DWD и источник ([AlertDetails]); нажатие по
 * строке сворачивает. Раскрыта в [AlertsList] одна.
 */
@Composable
internal fun AlertCard(
    alert: WeatherAlert,
    area: String?,
    expanded: Boolean,
    now: Instant,
    zone: ZoneId,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onHeaderHeight: (Int) -> Unit = {},
    onOpenHeight: (Int) -> Unit = {},
) {
    val palette = LocalPogodaPalette.current
    val glass = LocalGlass.current
    val tint = levelColor(alert.level)
    val title = alertTitle(alert.kind)
    val level = levelLine(alert.level)
    val time = alertWhenText(alert, now, zone)
    val description = listOf(title, level, time).filter { it.isNotEmpty() }.joinToString(", ")
    Column(
        modifier
            .fillMaxWidth()
            .alertGlass(AlertShape, glass, tint),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = AlertRowHeight)
                .onSizeChanged { onHeaderHeight(it.height) }
                .clickable(onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                }
                .padding(start = 12.dp, top = 5.dp, end = 10.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LineIconView(if (alert.kind.isSnowflake()) LineIcons.Ice else levelIcon(alert.level), 20.dp, tint)
            Column(Modifier.weight(1f)) {
                Text(title, style = pogodaText(13.5f, 500, color = palette.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(level, style = pogodaText(12f, 400, color = palette.ink2), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Время по центру плашки, на одной линии со значком, а не на строке заголовка (Дима 25.11.2026).
            if (time.isNotEmpty()) {
                Text(time, style = pogodaText(12f, 500, color = palette.ink2, tabular = true), maxLines = 1)
            }
        }
        // Подробности раскрываются и сворачиваются одинаково плавно: высота и прозрачность за [EXPAND_MS] (0.8.111;
        // раньше при сворачивании они пропадали сразу, а пустое место сжималось потом).
        AnimatedVisibility(expanded, enter = ExpandIn, exit = ExpandOut) {
            // Высота раскрытой части замеряется целиком (с полями), ещё до конца анимации: по ней «Главная» сразу
            // считает, насколько сжать верхний блок.
            Column(
                Modifier.onSizeChanged { onOpenHeight(it.height) }.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(palette.ink.copy(alpha = DIVIDER_ALPHA)))
                AlertDetails(alert, area, now, zone)
            }
        }
    }
}

/** Строка под списком: «Ещё N предупреждений» (раскрывает весь список) или «Свернуть». Материал кнопки, как в макете. */
@Composable
internal fun AlertMoreRow(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val glass = LocalGlass.current
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = AlertMoreHeight)
            .glassButton(MoreShape, glass)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = text
                role = Role.Button
            }
            .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = pogodaText(13f, 600, color = palette.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Список предупреждений на «Главной»: первые [shown] по порядку (самые серьёзные сверху) и, если [more], строка
 * «Ещё N», которая раскрывает весь список на месте; тогда внизу «Свернуть» ([onCollapse]). Раскрыта одна плашка
 * ([expandedId]); если она оказалась среди скрытых (открыта из уведомления), показывается весь список.
 */
@Composable
internal fun AlertsList(
    alerts: List<WeatherAlert>,
    shown: Int,
    more: Boolean,
    expandedId: String?,
    all: Boolean,
    area: String?,
    now: Instant,
    zone: ZoneId,
    onToggle: (String) -> Unit,
    onAll: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    onRowHeight: (Int) -> Unit = {},
    onMoreHeight: (Int) -> Unit = {},
    onOpenHeight: (Int) -> Unit = {},
) {
    val hidden = expandedId != null && alerts.indexOfFirst { it.id == expandedId } >= shown
    val everything = all || hidden
    // Лишние строки и строка «Ещё N» не появляются и не пропадают рывком: каждая уезжает и гаснет сама за [EXPAND_MS],
    // промежуток между строками лежит внутри анимируемой части (иначе при сворачивании оставались бы пустые зазоры).
    Column(modifier.fillMaxWidth()) {
        alerts.forEachIndexed { index, alert ->
            AnimatedVisibility(everything || index < shown, enter = ExpandIn, exit = ExpandOut) {
                AlertCard(
                    alert = alert,
                    area = area,
                    expanded = alert.id == expandedId,
                    now = now,
                    zone = zone,
                    onClick = { onToggle(alert.id) },
                    modifier = Modifier.padding(top = if (index > 0) AlertGap else 0.dp),
                    onHeaderHeight = if (index == 0) onRowHeight else NoMeasure,
                    onOpenHeight = onOpenHeight,
                )
            }
        }
        val count = alerts.size - shown
        AnimatedVisibility(everything && count > 0, enter = ExpandIn, exit = ExpandOut) {
            AlertMoreRow(stringResource(R.string.alerts_collapse), onCollapse, Modifier.padding(top = AlertGap))
        }
        AnimatedVisibility(!everything && more && count > 0, enter = ExpandIn, exit = ExpandOut) {
            AlertMoreRow(
                pluralStringResource(R.plurals.alerts_more, count, count),
                onAll,
                Modifier.padding(top = AlertGap).onSizeChanged { onMoreHeight(it.height) },
            )
        }
    }
}

/** Высоту этой плашки никто не меряет. */
private val NoMeasure: (Int) -> Unit = {}

/** Сколько длится раскрытие и сворачивание. */
private const val EXPAND_MS = 300

private val ExpandIn = fadeIn(tween(EXPAND_MS)) + expandVertically(tween(EXPAND_MS))
private val ExpandOut = fadeOut(tween(EXPAND_MS)) + shrinkVertically(tween(EXPAND_MS))

/** Прозрачность черты между строкой плашки и её подробностями (как черта в панели часа). */
private const val DIVIDER_ALPHA = 0.14f
