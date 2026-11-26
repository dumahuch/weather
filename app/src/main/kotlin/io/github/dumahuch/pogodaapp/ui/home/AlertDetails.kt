package io.github.dumahuch.pogodaapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.forecast.clockText
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.AlertLevel
import io.github.dumahuch.pogodaapp.weather.AlertWhen
import io.github.dumahuch.pogodaapp.weather.WeatherAlert
import java.time.Instant
import java.time.ZoneId

private val OriginalShape = RoundedCornerShape(16.dp)

/**
 * Одно предупреждение: значок и название явления, шкала уровня, период и район, оригинальный текст DWD (на немецком,
 * при английском интерфейсе английская версия DWD; решение Димы) и источник со временем выпуска.
 */
@Composable
internal fun AlertDetails(alert: WeatherAlert, area: String?, now: Instant, zone: ZoneId) {
    val palette = LocalPogodaPalette.current
    val english = LocalLocale.current.platformLocale.language == "en"
    LevelMeter(alert.level)
    InfoRow(stringResource(R.string.alert_valid), periodText(alert, now, zone))
    if (area != null) InfoRow(stringResource(R.string.alert_area), area)
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.button2.copy(alpha = palette.button2.alpha / 2), OriginalShape)
            .padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 13.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.alert_original), style = pogodaText(12.5f, 600, color = palette.ink2))
        Text(if (english) alert.headlineEn else alert.headlineDe, style = pogodaText(13f, 700, color = palette.ink, lineHeight = 1.35f))
        val description = if (english) alert.descriptionEn else alert.descriptionDe
        if (description.isNotEmpty()) Text(description, style = pogodaText(13.5f, 400, color = palette.ink, lineHeight = 1.5f))
        val instruction = if (english) alert.instructionEn else alert.instructionDe
        if (!instruction.isNullOrEmpty()) Text(instruction, style = pogodaText(13.5f, 400, color = palette.ink, lineHeight = 1.5f))
    }
    val issued = alert.issued
    Text(
        if (issued == null) stringResource(R.string.alert_source) else stringResource(R.string.alert_issued, moment(issued, now, zone)),
        style = pogodaText(12.5f, 500, color = palette.ink2, lineHeight = 1.4f),
    )
}

/** Шкала уровня: четыре черты, закрашены до уровня его цветом, и подпись «Уровень 2 из 4 · Выраженная погода». */
@Composable
private fun LevelMeter(level: AlertLevel) {
    val palette = LocalPogodaPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            AlertLevel.entries.forEach { step ->
                val color = if (step.number <= level.number) levelColor(step) else palette.button2
                Box(Modifier.size(width = 20.dp, height = 7.dp).background(color, RoundedCornerShape(4.dp)))
            }
        }
        Text(
            stringResource(R.string.alert_level_of, level.number, levelName(level)),
            style = pogodaText(13f, 600, color = palette.ink),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val palette = LocalPogodaPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(label, style = pogodaText(13.5f, 500, color = palette.ink2, lineHeight = 1.35f))
        Text(value, style = pogodaText(13.5f, 600, color = palette.ink, lineHeight = 1.35f), modifier = Modifier.weight(1f))
    }
}

/** День для начала строки: «Сегодня», «Завтра» или «Пт». */
@Composable
private fun dayWord(days: Int, at: Instant, zone: ZoneId, start: Boolean): String = when (days) {
    0 -> stringResource(if (start) R.string.today else R.string.alert_day_today_mid)
    1 -> stringResource(if (start) R.string.alert_day_tomorrow else R.string.alert_day_tomorrow_mid)
    else -> stringArrayResource(R.array.days_short)[at.atZone(zone).dayOfWeek.value - 1]
}

/** «Сегодня, 15:00–21:00», «Сегодня 15:00 – завтра 06:00» или «Сегодня, с 15:00» (если конец не известен). */
@Composable
private fun periodText(alert: WeatherAlert, now: Instant, zone: ZoneId): String {
    val start = alert.onset
    val startDays = AlertWhen.daysBetween(now, start, zone)
    val startWord = dayWord(startDays, start, zone, start = true)
    val end = alert.expires ?: return stringResource(R.string.alert_period_open, startWord, clockText(start, zone))
    val endDays = AlertWhen.daysBetween(now, end, zone)
    return if (endDays == startDays) {
        stringResource(R.string.alert_period_day, startWord, clockText(start, zone), clockText(end, zone))
    } else {
        stringResource(R.string.alert_period_span, startWord, clockText(start, zone), dayWord(endDays, end, zone, start = false), clockText(end, zone))
    }
}

/** Время выпуска: «14:20» сегодня, иначе с днём недели «Пн 22:10». */
@Composable
private fun moment(at: Instant, now: Instant, zone: ZoneId): String {
    val time = clockText(at, zone)
    return if (AlertWhen.daysBetween(now, at, zone) == 0) time else "${stringArrayResource(R.array.days_short)[at.atZone(zone).dayOfWeek.value - 1]} $time"
}
