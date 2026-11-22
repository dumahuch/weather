package io.github.dumahuch.pogodaapp.ui.metric

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.AppVersion
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.data.BrightSkyRadarClient
import io.github.dumahuch.pogodaapp.data.RadarParser
import io.github.dumahuch.pogodaapp.home.precipitationText
import io.github.dumahuch.pogodaapp.location.AssetPollenRegionLocator
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.forecast.clockText
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.ui.sunmoon.Card
import io.github.dumahuch.pogodaapp.ui.sunmoon.CardTitle
import io.github.dumahuch.pogodaapp.weather.ForecastException
import io.github.dumahuch.pogodaapp.weather.Place
import io.github.dumahuch.pogodaapp.weather.RadarFrame
import io.github.dumahuch.pogodaapp.weather.RadarStrip
import io.github.dumahuch.pogodaapp.weather.RainOutlook
import io.github.dumahuch.pogodaapp.weather.RainSoon
import java.time.Instant
import java.time.ZoneId

/** Что знает карточка радара: ещё грузится, не получилось, место вне Германии (карточки нет) или кадры. */
private sealed interface RadarState {
    data object Loading : RadarState
    data object Failed : RadarState
    data object Outside : RadarState
    class Ready(val frames: List<RadarFrame>, val at: Instant) : RadarState
}

/**
 * «Ближайшие 2 часа» (решение Димы 22.11.2026): радар DWD над местом, кадры по пять минут столбиками цвета силы и фраза
 * «Дождь начнётся примерно через N мин». Только сегодня и только в Германии (радар DWD); вне Германии карточки нет.
 * Пока радар грузится, место под карточку уже занято, чтобы страница не прыгала.
 */
@Composable
internal fun RadarCard(place: Place, zone: ZoneId) {
    val context = LocalContext.current.applicationContext
    val state by produceState<RadarState>(RadarState.Loading, place.latitude, place.longitude) {
        value = if (AssetPollenRegionLocator(context).keyOf(place.latitude, place.longitude) == null) {
            RadarState.Outside
        } else {
            val now = Instant.now()
            try {
                val client = BrightSkyRadarClient(userAgent = "PogodaApp/${AppVersion.read(context).name}", ahead = BrightSkyRadarClient.FULL_AHEAD)
                RadarParser.parse(client.fetchRaw(place.latitude, place.longitude, now))?.let { RadarState.Ready(it, now) } ?: RadarState.Failed
            } catch (e: ForecastException) {
                // Сеть, сервис, разбор: карточка честно говорит, что радара сейчас нет; остальная страница не страдает.
                RadarState.Failed
            }
        }
    }
    val current = state
    if (current == RadarState.Outside) return
    val palette = LocalPogodaPalette.current
    val locale = LocalLocale.current.platformLocale
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CardTitle(stringResource(R.string.radar_title))
            Text(stringResource(R.string.radar_source), style = pogodaText(11f, 500, color = palette.ink2), maxLines = 1)
        }
        val (headline, note) = when (current) {
            RadarState.Loading -> stringResource(R.string.radar_loading) to null
            is RadarState.Ready -> {
                val strip = RadarStrip.of(current.frames, current.at)
                val spell = RadarStrip.spell(strip)
                val words = when (val outlook = RainSoon.outlook(current.frames, current.at, RadarStrip.SPAN)) {
                    RainOutlook.Raining -> stringResource(R.string.radar_raining)
                    is RainOutlook.Starts -> if (outlook.inMinutes <= RainSoon.STEP.toMinutes()) {
                        stringResource(R.string.notify_rain_now)
                    } else {
                        stringResource(R.string.notify_rain_in, RainSoon.roundedMinutes(outlook.inMinutes))
                    }
                    RainOutlook.Dry -> stringResource(R.string.radar_dry)
                    RainOutlook.Unknown -> stringResource(R.string.radar_failed)
                }
                words to spell?.let { stringResource(R.string.radar_until, clockText(it.end, zone), precipitationText(it.totalMm, locale)) }
            }
            else -> stringResource(R.string.radar_failed) to null
        }
        Text(headline, style = pogodaText(15f, 700, color = palette.ink), modifier = Modifier.padding(top = 8.dp))
        if (note != null) Text(note, style = pogodaText(13f, 500, color = palette.ink2), modifier = Modifier.padding(top = 2.dp))
        if (current is RadarState.Ready) {
            val strip = RadarStrip.of(current.frames, current.at)
            if (strip.isNotEmpty()) {
                RadarBars(strip, Modifier.fillMaxWidth().height(RADAR_BARS_DP.dp).padding(top = 10.dp).clearAndSetSemantics { })
                RadarAxis(strip, zone)
            }
        }
    }
}

/** Столбики кадров: высота по силе (шкала не ниже [MIN_RADAR_SCALE] мм/ч, как у круга), цвет ступени; сухой кадр черточкой. */
@Composable
private fun RadarBars(strip: List<RadarFrame>, modifier: Modifier) {
    val palette = LocalPogodaPalette.current
    Canvas(modifier) {
        val slot = size.width / RadarStrip.FRAMES
        val bar = slot * BAR_SHARE
        val max = maxOf(MIN_RADAR_SCALE, strip.maxOf { it.mmPerHour })
        val floor = 2.dp.toPx()
        strip.forEachIndexed { i, frame ->
            val x = i * slot + (slot - bar) / 2
            if (frame.mmPerHour < RainSoon.THRESHOLD_MM_PER_HOUR) {
                drawRoundRect(palette.ink.copy(alpha = 0.22f), Offset(x, size.height - floor), Size(bar, floor), CornerRadius(floor / 2))
            } else {
                val h = maxOf(floor * 2, (frame.mmPerHour / max).toFloat().coerceAtMost(1f) * size.height)
                drawRoundRect(RainColors[rainLevel(frame.mmPerHour)], Offset(x, size.height - h), Size(bar, h), CornerRadius(minOf(bar / 2, 3.dp.toPx())))
            }
        }
    }
}

/** Подписи под столбиками: «сейчас» слева, часы через час и в конце окна. */
@Composable
private fun RadarAxis(strip: List<RadarFrame>, zone: ZoneId) {
    val style = pogodaText(11f, 500, color = LocalPogodaPalette.current.ink2, tabular = true)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(stringResource(R.string.radar_axis_now), style = style, modifier = Modifier.weight(1f), maxLines = 1)
        strip.getOrNull(strip.size / 2)?.let {
            Text(clockText(it.time, zone), style = style, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1)
        }
        Text(clockText(strip.last().time, zone), style = style, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1)
    }
}

private const val RADAR_BARS_DP = 46
private const val BAR_SHARE = 0.62f

/** Наименьший верх шкалы столбиков радара, мм/ч: слабый дождь не должен выглядеть ливнем (как у круга суток). */
private const val MIN_RADAR_SCALE = 1.5
