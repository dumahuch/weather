package io.github.dumahuch.pogodaapp.ui.forecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.CompassPoint
import io.github.dumahuch.pogodaapp.forecast.MIN_NOTICEABLE_MM
import io.github.dumahuch.pogodaapp.forecast.MetricDays
import io.github.dumahuch.pogodaapp.forecast.Mugginess
import io.github.dumahuch.pogodaapp.forecast.PressureDay
import io.github.dumahuch.pogodaapp.forecast.PressureLevel
import io.github.dumahuch.pogodaapp.forecast.PressureUnits
import io.github.dumahuch.pogodaapp.forecast.PrecipitationKind
import io.github.dumahuch.pogodaapp.forecast.beaufortOf
import io.github.dumahuch.pogodaapp.forecast.continuationCase
import io.github.dumahuch.pogodaapp.forecast.dayPrecipitationText
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.details.BigValue
import io.github.dumahuch.pogodaapp.ui.details.SubText
import io.github.dumahuch.pogodaapp.ui.metric.DewBands
import io.github.dumahuch.pogodaapp.ui.metric.DewColors
import io.github.dumahuch.pogodaapp.ui.metric.RainColors
import io.github.dumahuch.pogodaapp.ui.metric.dewFraction
import io.github.dumahuch.pogodaapp.ui.metric.kindName
import io.github.dumahuch.pogodaapp.ui.metric.rainLevel
import io.github.dumahuch.pogodaapp.ui.pages.PressureBands
import io.github.dumahuch.pogodaapp.ui.pages.PressureColors
import io.github.dumahuch.pogodaapp.ui.pages.StepGauge
import io.github.dumahuch.pogodaapp.ui.pages.pressureFraction
import io.github.dumahuch.pogodaapp.ui.pages.pressureLevelLabel
import io.github.dumahuch.pogodaapp.ui.pages.trendLabel
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.DayForecast
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.HourDetails
import io.github.dumahuch.pogodaapp.weather.HourPoint
import kotlin.math.roundToInt

/*
 * Подробности дня (0.8.106, решение Димы 26.11.2026): плитки Осадки, Ветер, Давление, Влажность и Облачность на всю
 * ширину. «Световой день», «УФ» и «Ощущается как» убраны. Значения за сутки считаются по часам этого дня из прогноза
 * Open-Meteo; слова и цвета те же, что на страницах показателей. Плитки не нажимаются.
 */

/** Полукруг на плитке дня, как на плитках «Подробно». */
private val TILE_GAUGE = 100.dp

/** Облачность делится на ясно до 25 %, переменная до 60 %, дальше пасмурно (выбрано Claude). */
private const val CLEAR_BELOW_PERCENT = 25
private const val PARTLY_BELOW_PERCENT = 60

private const val BEAUFORT_TOP = 12f

/** Данные дня для плиток: часы с подробностями и почасовые точки. */
private class DayData(val forecast: Forecast, val day: DayForecast) {
    val details: List<HourDetails> = MetricDays.detailsOf(forecast.hourDetails, forecast.zone, day.date)
    val points: List<HourPoint> = forecast.hoursOf(day.date)
}

@Composable
internal fun DayTiles(forecast: Forecast, day: DayForecast, modifier: Modifier = Modifier) {
    val data = remember(forecast, day) { DayData(forecast, day) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
            PrecipitationDayTile(data, Modifier.weight(1f).fillMaxHeight())
            WindDayTile(data, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TILE_SPACING)) {
            PressureDayTile(data, Modifier.weight(1f).fillMaxHeight())
            HumidityDayTile(data, Modifier.weight(1f).fillMaxHeight())
        }
        CloudDayTile(data, Modifier.fillMaxWidth())
    }
}

private val TILE_SPACING = 8.dp

/** Одна фраза о дне под значком: когда осадки и какой ветер («Дождь днём. Слабый ветер, юго-западный.»). */
@Composable
internal fun dayPhrase(forecast: Forecast, day: DayForecast): String {
    val locale = LocalLocale.current.platformLocale
    val data = remember(forecast, day) { DayData(forecast, day) }
    val parts = MetricDays.partPeaks(data.points, forecast.zone)
    val whenWords = stringArrayResource(R.array.metric_parts_when)
    val wet = parts.indices.filter { parts[it] >= MIN_NOTICEABLE_MM }.map { whenWords[it] }
    val kind = data.points.firstOrNull { it.precipitationMm >= MIN_NOTICEABLE_MM }?.let { PrecipitationKind.of(it.condition) }
    val rain = if (day.precipitationMm >= MIN_NOTICEABLE_MM && wet.isNotEmpty() && kind != null) {
        val and = stringResource(R.string.metric_and)
        val joined = if (wet.size > 1) wet.dropLast(1).joinToString(", ") + " $and " + wet.last() else wet.first()
        stringResource(R.string.metric_kind_when, kindName(kind), joined)
    } else {
        stringResource(R.string.next24_none)
    }
    val strength = stringArrayResource(R.array.beaufort_names)[beaufortOf(day.windMaxKmh.roundToInt())]
    val directions = stringArrayResource(R.array.wind_directions)
    val direction = MetricDays.dominantDirection(data.details)?.let { continuationCase(directions[CompassPoint.of(it).ordinal], locale) }
    val wind = listOfNotNull(strength, direction).joinToString(", ")
    return "$rain. $wind."
}

@Composable
private fun PrecipitationDayTile(data: DayData, modifier: Modifier) {
    val locale = LocalLocale.current.platformLocale
    val total = data.day.precipitationMm
    val peak = data.points.maxOfOrNull { it.precipitationMm } ?: 0.0
    val kind = data.points.firstOrNull { it.precipitationMm >= MIN_NOTICEABLE_MM }?.let { PrecipitationKind.of(it.condition) }
    val wet = total >= MIN_NOTICEABLE_MM && kind != null
    DayTile(
        title = stringResource(R.string.tile_precipitation),
        value = dayPrecipitationText(total, locale).ifEmpty { "0" },
        unit = stringResource(R.string.mm_unit),
        word = if (wet) kindName(kind!!) else stringResource(R.string.day_no_precip),
        wordDot = if (wet) RainColors[rainLevel(peak)] else null,
        sub = data.day.precipitationProbability?.let { stringResource(R.string.precip_chance, percentText(it)) },
        modifier = modifier,
    )
}

@Composable
private fun WindDayTile(data: DayData, modifier: Modifier) {
    val palette = LocalPogodaPalette.current
    val kmh = stringResource(R.string.kmh_unit)
    val speed = data.day.windMaxKmh.roundToInt()
    val step = beaufortOf(speed)
    val directions = stringArrayResource(R.array.wind_directions)
    val direction = MetricDays.dominantDirection(data.details)?.let { directions[CompassPoint.of(it).ordinal] }
    DayTile(
        title = stringResource(R.string.tile_wind),
        value = speed.toString(),
        unit = kmh,
        word = stringArrayResource(R.array.beaufort_names)[step],
        wordDot = lerp(palette.accent, palette.warning, step / BEAUFORT_TOP),
        sub = listOfNotNull(direction, stringResource(R.string.gusts_up_to, data.day.gustMaxKmh.roundToInt(), kmh)).joinToString("\n"),
        modifier = modifier,
    )
}

@Composable
private fun PressureDayTile(data: DayData, modifier: Modifier) {
    val hpa = PressureDay.noon(data.details, data.forecast.zone)
    // В русском интерфейсе миллиметры ртутного столба (решение Димы 20.11), уровень считается в гПа.
    val mmHg = booleanResource(R.bool.pressure_mmhg)
    val locale = LocalLocale.current.platformLocale
    if (hpa == null) {
        DayTile(stringResource(R.string.tile_pressure), "–", null, null, null, null, modifier)
        return
    }
    val level = stringResource(pressureLevelLabel(PressureLevel.ofHpa(hpa.roundToInt())))
    val trend = PressureDay.trend(data.details)
    val word = trend?.let { stringResource(R.string.pressure_trend_level, level, continuationCase(stringResource(trendLabel(it)), locale)) } ?: level
    DayTile(
        title = stringResource(R.string.tile_pressure),
        value = PressureUnits.shown(hpa, mmHg).roundToInt().toString(),
        unit = stringResource(R.string.pg_pressure_short),
        word = word,
        wordDot = null,
        sub = null,
        modifier = modifier,
        symbol = { StepGauge(pressureFraction(hpa), PressureBands, PressureColors, width = TILE_GAUGE) },
    )
}

@Composable
private fun HumidityDayTile(data: DayData, modifier: Modifier) {
    val palette = LocalPogodaPalette.current
    val humidity = data.details.mapNotNull { it.humidity }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    // Ощущение по самой высокой точке росы дня, как в списке дней страницы влажности.
    val dew = data.details.mapNotNull { it.dewPointC }.maxOrNull()
    val fog = data.details.mapNotNull { it.visibilityM }.minOrNull()?.let { it < FOG_BELOW_M } == true
    val mugginess = stringArrayResource(R.array.mugginess_levels)
    val step = dew?.let { Mugginess.ofDewPoint(it) }
    val word = when {
        fog -> stringResource(R.string.metric_fog)
        dew != null -> step?.let { mugginess[it.ordinal] } ?: stringResource(R.string.metric_comfortable)
        else -> null
    }
    DayTile(
        title = stringResource(R.string.tile_humidity),
        value = humidity?.toString() ?: "–",
        unit = if (humidity != null) "%" else null,
        word = word,
        wordDot = when {
            fog || dew == null -> palette.ink2
            else -> DewColors[step?.let { it.ordinal + 1 } ?: 0]
        },
        sub = dew?.let { "${stringResource(R.string.hour_dew_point)} ${temperatureText(it)}" },
        modifier = modifier,
        symbol = dew?.let { { StepGauge(dewFraction(it), DewBands, DewColors, width = TILE_GAUGE) } },
    )
}

/** Туман: видимость меньше километра (ступень Met Office «очень плохая»), как на странице влажности. */
private const val FOG_BELOW_M = 1_000.0

@Composable
private fun CloudDayTile(data: DayData, modifier: Modifier) {
    val palette = LocalPogodaPalette.current
    val cover = data.points.mapNotNull { it.cloudCover }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val low = data.details.mapNotNull { it.cloudLowPercent }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val mid = data.details.mapNotNull { it.cloudMidPercent }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val high = data.details.mapNotNull { it.cloudHighPercent }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val word = cover?.let {
        stringResource(
            when {
                it < CLEAR_BELOW_PERCENT -> R.string.wx_clear
                it < PARTLY_BELOW_PERCENT -> R.string.wx_partly
                else -> R.string.wx_cloudy
            },
        )
    }
    val layers = if (low != null && mid != null && high != null) {
        listOf(low, mid, high)
    } else {
        null
    }
    DayTile(
        title = stringResource(R.string.tile_clouds),
        value = cover?.toString() ?: "–",
        unit = if (cover != null) "%" else null,
        word = word,
        wordDot = palette.ink2,
        sub = layers?.let { stringResource(R.string.day_cloud_layers, it[0], it[1], it[2]) },
        modifier = modifier,
        symbol = layers?.let { { CloudLayers(it) } },
        symbolAtEnd = true,
    )
}

/** Три полоски слоёв облаков (нижние, средние, верхние): чем длиннее закраска, тем больше неба закрыто слоем. */
@Composable
private fun CloudLayers(percent: List<Int>) {
    val palette = LocalPogodaPalette.current
    Canvas(Modifier.size(width = 120.dp, height = 44.dp)) {
        val barHeight = 8.dp.toPx()
        val gap = (size.height - 3 * barHeight) / 2
        percent.forEachIndexed { i, value ->
            val top = i * (barHeight + gap)
            drawRoundRect(palette.ink.copy(alpha = 0.14f), Offset(0f, top), Size(size.width, barHeight), CornerRadius(barHeight / 2))
            drawRoundRect(
                palette.ink2.copy(alpha = 0.85f - 0.2f * i),
                Offset(0f, top),
                Size(size.width * value.coerceIn(0, 100) / 100f, barHeight),
                CornerRadius(barHeight / 2),
            )
        }
    }
}

/**
 * Плитка дня: заголовок, крупное значение, слово с точкой цвета ступени, пояснение и символ ([symbol]) внизу по центру
 * или, у плитки на всю ширину ([symbolAtEnd]), справа. Размеры как у плиток «Подробно» (радиус 22 dp, поля 14 × 12).
 */
@Composable
private fun DayTile(
    title: String,
    value: String,
    unit: String?,
    word: String?,
    wordDot: Color?,
    sub: String?,
    modifier: Modifier = Modifier,
    symbol: (@Composable () -> Unit)? = null,
    symbolAtEnd: Boolean = false,
) {
    val palette = LocalPogodaPalette.current
    val texts: @Composable (Modifier) -> Unit = { textModifier ->
        Column(textModifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = pogodaText(12.5f, 700, color = palette.ink2), maxLines = 1)
            BigValue(value, unit)
            if (word != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (wordDot != null) Box(Modifier.padding(end = 7.dp).size(9.dp).background(wordDot, CircleShape))
                    Text(word, style = pogodaText(14f, 600, lineHeight = 1.25f), maxLines = 2)
                }
            }
            if (sub != null) SubText(sub)
        }
    }
    Column(
        modifier
            .glassCard(RoundedCornerShape(22.dp), LocalGlass.current)
            .padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 12.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        if (symbolAtEnd && symbol != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                texts(Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                symbol()
            }
        } else {
            texts(Modifier)
            if (symbol != null) {
                // Символ прижат к низу: соседняя плитка в ряду может быть выше.
                Spacer(Modifier.weight(1f).height(6.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { symbol() }
            }
        }
    }
}
