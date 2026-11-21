package io.github.dumahuch.pogodaapp.ui.sunmoon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.SunDay
import io.github.dumahuch.pogodaapp.forecast.SunLight
import io.github.dumahuch.pogodaapp.home.HomeUiState
import io.github.dumahuch.pogodaapp.sky.Moon
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.bottomContentPadding
import io.github.dumahuch.pogodaapp.ui.forecast.PageTitle
import io.github.dumahuch.pogodaapp.ui.forecast.clockText
import io.github.dumahuch.pogodaapp.ui.forecast.dayName
import io.github.dumahuch.pogodaapp.ui.forecast.fullDate
import io.github.dumahuch.pogodaapp.ui.forecast.percentText
import io.github.dumahuch.pogodaapp.ui.home.StateCard
import io.github.dumahuch.pogodaapp.ui.metric.DayValue
import io.github.dumahuch.pogodaapp.ui.metric.DaysCard
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.ui.sky.ReportSkyScroll
import io.github.dumahuch.pogodaapp.weather.Forecast
import io.github.dumahuch.pogodaapp.weather.Place
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * Страница «Солнце и Луна» (шаг В, 21.11.2026, макет https://claude.ai/artifact/1nRbyKdjC7NTVMDYS1g1B3). Открывается
 * нажатием на плитку «Подробно», закрывается жестом «назад». Карточки: Солнце (кривая пути, световой день, полдень, где
 * восходит и садится), свет для съёмки (полоса суток, золотой и синий час, облака в эти часы), сумерки, Луна, 7 дней.
 * Нажатие на день в списке показывает его выше. Солнце и луна считаются на телефоне, облака берутся из прогноза.
 */
@Composable
fun SunMoonScreen(state: HomeUiState.Content, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val forecast = state.forecast
    val place = state.place
    val zone = forecast.zone
    val now = remember(forecast) { Instant.now() }
    val today = remember(forecast, now) { now.atZone(zone).toLocalDate() }
    // Свет суток со вчера (разница длины дня) до седьмого дня (ночь последнего дня кончается завтра).
    val days = remember(place, zone, today) {
        (-1L..DAYS_SHOWN.toLong()).associate { offset ->
            val date = today.plusDays(offset)
            date to SunLight.day(date, zone, place.latitude, place.longitude)
        }
    }
    val dates = remember(today) { (0L until DAYS_SHOWN).map { today.plusDays(it) } }
    var chosen by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val date = LocalDate.ofEpochDay(chosen).takeIf { it in dates } ?: today
    val locale = LocalLocale.current.platformLocale
    val dayNames = stringArrayResource(R.array.days_short).toList()
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    ReportSkyScroll(scroll)
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scroll),
    ) {
        PageTitle(
            stringResource(R.string.tile_sun_moon),
            "${dayName(date, dayNames)}, ${fullDate(date, locale)}",
            Modifier.padding(start = 24.dp, top = 10.dp, end = 24.dp),
        )
        Spacer(Modifier.height(12.dp))
        val side = Modifier.padding(horizontal = 20.dp)
        val day = days[date]
        if (day == null) {
            StateCard(
                icon = LineIcons.Grid,
                title = stringResource(R.string.details_missing_title),
                text = stringResource(R.string.details_missing_text),
                buttonLabel = stringResource(R.string.retry),
                onButton = onRetry,
                modifier = side,
            )
        } else {
            val ctx = SunMoonContext(place, forecast, zone, now, today, date, days)
            Column(side, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SunCard(ctx, day)
                LightCard(ctx, day)
                TwilightCard(day, zone)
                MoonCard(ctx)
                WeekCard(ctx, dates) { picked ->
                    chosen = picked.toEpochDay()
                    scope.launch { scroll.animateScrollTo(0) }
                }
            }
        }
        Spacer(Modifier.height(bottomContentPadding()))
    }
}

/** Общие данные карточек: место, прогноз, «сейчас», выбранный день и свет суток по датам. */
internal class SunMoonContext(
    val place: Place,
    val forecast: Forecast,
    val zone: ZoneId,
    val now: Instant,
    val today: LocalDate,
    val date: LocalDate,
    val days: Map<LocalDate, SunDay>,
) {
    val isToday: Boolean get() = date == today
}

private const val DAYS_SHOWN = 7

/** «3 ч 12 мин» или «45 мин». */
@Composable
internal fun durationText(duration: Duration): String {
    val minutes = duration.toMinutes().coerceAtLeast(0)
    return if (minutes >= 60) {
        stringResource(R.string.hours_minutes, (minutes / 60).toInt(), (minutes % 60).toInt())
    } else {
        stringResource(R.string.minutes_only, minutes.toInt())
    }
}

/** Список «7 дней»: восход и закат с длиной дня, начало вечернего золотого часа и облака в нём, фаза луны. */
@Composable
private fun WeekCard(ctx: SunMoonContext, dates: List<LocalDate>, onSelect: (LocalDate) -> Unit) {
    val palette = LocalPogodaPalette.current
    val north = ctx.place.latitude >= 0
    DaysCard(
        dates = dates,
        today = ctx.today,
        selected = ctx.date,
        onSelect = onSelect,
        note = "",
        legend = {
            Row(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sun_moon_days_sun), style = pogodaText(11.5f, 600, color = palette.ink2), modifier = Modifier.weight(1f))
                Text(stringResource(R.string.sun_moon_days_golden), style = pogodaText(11.5f, 600, color = palette.ink2))
            }
        },
    ) { index ->
        val date = dates[index]
        val day = ctx.days[date]
        val rise = day?.sunrise?.let { clockText(it, ctx.zone) } ?: "–"
        val set = day?.sunset?.let { clockText(it, ctx.zone) } ?: "–"
        Column(Modifier.weight(1f)) {
            Text("$rise – $set", style = pogodaText(14.5f, 600, tabular = true), maxLines = 1)
            day?.daylight?.let { Text(durationText(it), style = pogodaText(11f, 500, color = palette.ink2), maxLines = 1) }
        }
        val golden = day?.eveningGolden
        val clouds = golden?.let { goldenClouds(ctx.forecast, it) }
        DayValue(
            golden?.let { clockText(it.from, ctx.zone) } ?: "–",
            clouds?.let { stringResource(R.string.sun_moon_days_clouds, percentText(it.percent)) },
        )
        val phase = remember(date, ctx.zone) { Moon.phaseAt(date.atTime(12, 0).atZone(ctx.zone).toInstant()) }
        MoonGlyph(phase.illumination, litOnRight = phase.waxing == north, modifier = Modifier.size(22.dp))
    }
}

/** Таблица «подпись · утро · вечер» карточек света и сумерек. */
@Composable
internal fun TimeTable(rows: List<TimeRow>, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.sun_moon_morning), style = pogodaText(11.5f, 600, color = palette.ink2), modifier = Modifier.width(TIME_COLUMN))
            Text(stringResource(R.string.sun_moon_evening), style = pogodaText(11.5f, 600, color = palette.ink2), modifier = Modifier.width(TIME_COLUMN))
        }
        rows.forEach { row ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.glassLine))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (row.color != null) {
                            Box(Modifier.size(10.dp).background(rgb(row.color), RoundedCornerShape(3.dp)))
                            Spacer(Modifier.width(7.dp))
                        }
                        Text(row.label, style = pogodaText(13.5f, 600), maxLines = 1)
                    }
                    row.note?.let {
                        Text(
                            it,
                            style = pogodaText(11.5f, 500, color = palette.ink2),
                            modifier = Modifier.padding(start = if (row.color != null) 17.dp else 0.dp),
                        )
                    }
                }
                TimeCell(row.morning, row.morningNote)
                TimeCell(row.evening, row.eveningNote)
            }
        }
    }
}

@Composable
private fun TimeCell(value: String, note: String?) {
    val palette = LocalPogodaPalette.current
    Column(Modifier.width(TIME_COLUMN)) {
        Text(value, style = pogodaText(13.5f, 600, tabular = true), maxLines = 1)
        note?.let { Text(it, style = pogodaText(11f, 500, color = palette.ink2), maxLines = 2) }
    }
}

/** Строка таблицы: образец цвета (как на полосе), подпись с пояснением, значения утром и вечером. */
internal class TimeRow(
    val label: String,
    val morning: String,
    val evening: String,
    val color: Int? = null,
    val note: String? = null,
    val morningNote: String? = null,
    val eveningNote: String? = null,
)

private val TIME_COLUMN = 92.dp
