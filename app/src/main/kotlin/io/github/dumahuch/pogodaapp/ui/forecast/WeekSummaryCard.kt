package io.github.dumahuch.pogodaapp.ui.forecast

import android.icu.text.ListFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.DrySky
import io.github.dumahuch.pogodaapp.forecast.TemperatureTrend
import io.github.dumahuch.pogodaapp.forecast.WeekPrecipKind
import io.github.dumahuch.pogodaapp.forecast.WeekPrecipitation
import io.github.dumahuch.pogodaapp.forecast.WeekSummary
import io.github.dumahuch.pogodaapp.home.temperatureText
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.pogodaText
import java.time.LocalDate

/** Дней с осадками, начиная с которых говорим «почти каждый день» и с которых перечисляем дни сокращённо. */
private const val MOST_DAYS = 5
private const val SEVERAL_DAYS = 3

/**
 * Фраза недели над списком дней (макет 26.11.2026, вариант 2): «Сухо и пасмурно. От −4° до 4°. К концу недели теплеет.»
 * Правила сводки: [io.github.dumahuch.pogodaapp.forecast.WeekSummaries].
 */
@Composable
internal fun WeekSummaryCard(summary: WeekSummary, today: LocalDate, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Column(
        modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(24.dp), LocalGlass.current)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LineIconView(LineIcons.Calendar, 16.dp, palette.ink2, strokeWidth = 1.8f)
            Text(stringResource(R.string.week_title), style = pogodaText(12.5f, 700, color = palette.ink2))
        }
        Text(
            weekSummaryText(summary, today),
            style = pogodaText(16f, 600, lineHeight = 1.35f),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun weekSummaryText(summary: WeekSummary, today: LocalDate): String {
    val locale = LocalLocale.current.platformLocale
    val daysOn = stringArrayResource(R.array.week_days_on)
    val daysShort = stringArrayResource(R.array.days_short)
    val todayWord = stringResource(R.string.week_today)
    val tomorrowWord = stringResource(R.string.week_tomorrow)
    val list = ListFormatter.getInstance(locale)
    val precipitation = when (val p = summary.precipitation) {
        is WeekPrecipitation.Dry -> stringResource(
            when (p.sky) {
                DrySky.Clear -> R.string.week_dry_clear
                DrySky.Cloudy -> R.string.week_dry_cloudy
                DrySky.Plain -> R.string.week_dry
            },
        )
        is WeekPrecipitation.Wet -> when {
            p.dates.size >= MOST_DAYS -> stringResource(R.string.week_precip_most)
            p.dates.size >= SEVERAL_DAYS -> stringResource(
                if (p.possible) R.string.week_precip_several_maybe else R.string.week_precip_several,
                list.format(p.dates.map { daysShort[it.dayOfWeek.value - 1] }),
            )
            else -> {
                val whenText = list.format(
                    p.dates.map {
                        when (it) {
                            today -> todayWord
                            today.plusDays(1) -> tomorrowWord
                            else -> daysOn[it.dayOfWeek.value - 1]
                        }
                    },
                )
                stringResource(
                    when (p.kind) {
                        WeekPrecipKind.Rain -> if (p.possible) R.string.week_rain_maybe else R.string.week_rain
                        WeekPrecipKind.Snow -> if (p.possible) R.string.week_snow_maybe else R.string.week_snow
                        WeekPrecipKind.Mixed -> if (p.possible) R.string.week_precip_maybe else R.string.week_precip
                    },
                    whenText,
                )
            }
        }
    }
    val range = stringResource(
        R.string.week_temp_range,
        temperatureText(summary.lowC.toDouble()),
        temperatureText(summary.highC.toDouble()),
    )
    val trend = when (summary.trend) {
        TemperatureTrend.Warmer -> stringResource(R.string.week_trend_warmer)
        TemperatureTrend.Colder -> stringResource(R.string.week_trend_colder)
        TemperatureTrend.Steady -> null
    }
    return listOfNotNull(precipitation, range, trend).joinToString(". ") + "."
}
