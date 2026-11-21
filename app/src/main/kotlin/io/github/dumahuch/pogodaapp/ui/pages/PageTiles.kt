package io.github.dumahuch.pogodaapp.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.PressureLevel
import io.github.dumahuch.pogodaapp.forecast.PressureTrend
import io.github.dumahuch.pogodaapp.forecast.PressureUnits
import io.github.dumahuch.pogodaapp.forecast.UvLevel
import io.github.dumahuch.pogodaapp.forecast.continuationCase
import io.github.dumahuch.pogodaapp.ui.details.PollenDots
import io.github.dumahuch.pogodaapp.ui.forecast.uvLevelLabel
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.AirQuality
import io.github.dumahuch.pogodaapp.weather.HourDetails
import io.github.dumahuch.pogodaapp.weather.PollenForecast
import java.time.LocalDate
import kotlin.math.roundToInt

/*
 * Плитки «Подробно» (0.8.80): на плитке тот же символ, что на её странице (полукруг ступеней у воздуха, давления и УФ,
 * точки у пыльцы), стрелка › и нажатие открывает страницу.
 */

/** Полукруг на плитке чуть меньше, чем на странице. */
private val TILE_GAUGE = 100.dp

/** Пыльца на плитке: три вида. */
private const val TILE_POLLEN_KINDS = 3

@Composable
internal fun AirTile(air: AirQuality?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val levels = stringArrayResource(R.array.air_levels)
    PageTile(stringResource(R.string.tile_air), onOpen, modifier) {
        if (air == null) {
            NoData()
        } else {
            GaugeTileBody(air.eaqi.toString(), stringResource(R.string.air_unit), levels[air.level.ordinal]) {
                StepGauge(airFraction(air.eaqi), AirBands, AirColors, width = TILE_GAUGE)
            }
        }
    }
}

@Composable
internal fun PollenTile(pollen: PollenForecast?, today: LocalDate, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val kinds = stringArrayResource(R.array.pollen_kinds)
    val levels = stringArrayResource(R.array.pollen_levels)
    val day = pollen?.on(today)
    val rows = pollen?.shown(today, TILE_POLLEN_KINDS)
    PageTile(stringResource(R.string.tile_pollen), onOpen, modifier) {
        if (day == null || rows == null) {
            NoData()
        } else {
            val top = strongest(day)
            // Название вида крупно, но мельче числа соседних плиток: длинные названия («Амброзия») помещаются в строку.
            Text(
                top?.let { kinds[it.first.ordinal] } ?: stringResource(R.string.pg_pollen_none),
                style = pogodaText(26f, 300, lineHeight = 1.2f, letterSpacing = (-0.4f).sp),
                maxLines = 1,
            )
            Text(
                top?.let { levels[it.second.halves] } ?: stringResource(R.string.pg_pollen_none_word),
                style = pogodaText(14f, 600, lineHeight = 1.25f),
                maxLines = 2,
            )
            Spacer(Modifier.weight(1f).height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                rows.forEach { (kind, level) ->
                    Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(kinds[kind.ordinal], style = pogodaText(13f, 500), modifier = Modifier.weight(1f), maxLines = 1)
                        PollenDots(level, levels[level.halves])
                    }
                }
            }
        }
    }
}

@Composable
internal fun PressureTile(details: HourDetails?, trend: PressureTrend?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val hpa = details?.pressureHpa
    PageTile(stringResource(R.string.tile_pressure), onOpen, modifier) {
        if (hpa == null) {
            NoData()
        } else {
            // В русском интерфейсе миллиметры ртутного столба (решение Димы 20.11), уровень считается в гПа.
            val mmHg = booleanResource(R.bool.pressure_mmhg)
            val locale = LocalLocale.current.platformLocale
            val level = stringResource(pressureLevelLabel(PressureLevel.ofHpa(hpa.roundToInt())))
            // «Повышенное · растёт»: уровень и куда давление идёт в ближайшие три часа.
            val word = trend?.let { stringResource(R.string.pressure_trend_level, level, continuationCase(stringResource(trendLabel(it)), locale)) } ?: level
            GaugeTileBody(PressureUnits.shown(hpa, mmHg).roundToInt().toString(), stringResource(R.string.pg_pressure_short), word) {
                StepGauge(pressureFraction(hpa), PressureBands, PressureColors, width = TILE_GAUGE)
            }
        }
    }
}

@Composable
internal fun UvTile(details: HourDetails?, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val uv = details?.uvIndex?.roundToInt()
    PageTile(stringResource(R.string.tile_uv), onOpen, modifier) {
        if (uv == null) {
            NoData()
        } else {
            GaugeTileBody(uv.toString(), null, stringResource(uvLevelLabel(UvLevel.of(uv)))) {
                StepGauge(uvFraction(uv), UvBands, UvColors, width = TILE_GAUGE)
            }
        }
    }
}
