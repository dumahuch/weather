package io.github.dumahuch.pogodaapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
private const val TINT_FILL = 0.15f
private const val TINT_LINE = 0.26f

/** Цвет уровня DWD (приглушённый под стиль приложения). */
internal fun levelColor(level: AlertLevel): Color = PogodaColors.AlertLevels[level.number - 1]

/** Значок на плашке: треугольник у уровней 1 и 2, восьмиугольник у 3 и 4 (как в макете). */
internal fun levelIcon(level: AlertLevel): LineIcon =
    if (level.number >= AlertLevel.Severe.number) LineIcons.Severe else LineIcons.Warning

/** Значок явления в подробностях предупреждения. */
internal fun kindIcon(kind: AlertKind): LineIcon = when (kind) {
    AlertKind.Thunderstorm -> LineIcons.Storm
    AlertKind.Wind -> LineIcons.Wind
    AlertKind.Rain -> LineIcons.Rain
    AlertKind.Snow -> LineIcons.Snow
    AlertKind.Frost, AlertKind.Ice -> LineIcons.Ice
    AlertKind.Fog -> LineIcons.Fog
    AlertKind.Heat -> LineIcons.Thermometer
    AlertKind.Uv -> LineIcons.Sun
    AlertKind.Thaw -> LineIcons.Drop
    AlertKind.Other -> LineIcons.Warning
}

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
        .clip(shape)
        .border(1.dp, tint.copy(alpha = TINT_LINE), shape)
        .hazeCardBlur(glass)
        .background(fill, shape)
}

/**
 * Одна плашка: значок уровня, справа от него две строки и стрелка (открывает подробности). Первая строка: название
 * явления и время справа от него; вторая: уровень на всю ширину текста (не сокращается из-за времени).
 */
@Composable
internal fun AlertRow(alert: WeatherAlert, now: Instant, zone: ZoneId, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val glass = LocalGlass.current
    val tint = levelColor(alert.level)
    val title = alertTitle(alert.kind)
    val level = levelLine(alert.level)
    val time = alertWhenText(alert, now, zone)
    val description = listOf(title, level, time).filter { it.isNotEmpty() }.joinToString(", ")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = AlertRowHeight)
            .alertGlass(AlertShape, glass, tint)
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(start = 12.dp, top = 5.dp, end = 10.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LineIconView(levelIcon(alert.level), 20.dp, tint)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title,
                    style = pogodaText(13.5f, 500, color = palette.ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (time.isNotEmpty()) {
                    Text(time, style = pogodaText(12f, 500, color = palette.ink2, tabular = true), maxLines = 1)
                }
            }
            Text(level, style = pogodaText(12f, 400, color = palette.ink2), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        LineIconView(LineIcons.ChevronRight, 16.dp, palette.ink2)
    }
}

/** Строка «Ещё N предупреждений»: открывает весь список. Материал кнопки, как в макете. */
@Composable
internal fun AlertMoreRow(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val glass = LocalGlass.current
    val text = pluralStringResource(R.plurals.alerts_more, count, count)
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
        LineIconView(LineIcons.ChevronRight, 16.dp, palette.ink2)
    }
}

/**
 * Список предупреждений на «Главной»: первые [shown] по порядку (самые серьёзные сверху) и, если [more], строка
 * «Ещё N», которая открывает весь список ([onAll]).
 */
@Composable
internal fun AlertsList(
    alerts: List<WeatherAlert>,
    shown: Int,
    more: Boolean,
    now: Instant,
    zone: ZoneId,
    onOpen: (WeatherAlert) -> Unit,
    onAll: () -> Unit,
    modifier: Modifier = Modifier,
    onRowHeight: (Int) -> Unit = {},
    onMoreHeight: (Int) -> Unit = {},
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AlertGap)) {
        alerts.take(shown).forEachIndexed { index, alert ->
            val measure = if (index == 0) Modifier.onSizeChanged { onRowHeight(it.height) } else Modifier
            AlertRow(alert, now, zone, onClick = { onOpen(alert) }, modifier = measure)
        }
        if (more) AlertMoreRow(alerts.size - shown, onClick = onAll, modifier = Modifier.onSizeChanged { onMoreHeight(it.height) })
    }
}
