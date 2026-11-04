package io.github.dumahuch.pogodaapp.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.hazeCardBlur
import io.github.dumahuch.pogodaapp.ui.forecast.clockText
import io.github.dumahuch.pogodaapp.ui.pogodaText
import io.github.dumahuch.pogodaapp.weather.AlertLevel
import io.github.dumahuch.pogodaapp.weather.AlertWhen
import io.github.dumahuch.pogodaapp.weather.WeatherAlert
import java.time.Instant
import java.time.ZoneId

/** Что открыто в шторке предупреждений: весь список или одно предупреждение (из списка или прямо с «Главной»). */
sealed interface AlertSheet {
    data object All : AlertSheet

    data class One(val id: String, val fromList: Boolean) : AlertSheet
}

private val SheetShape = RoundedCornerShape(26.dp)
private val OriginalShape = RoundedCornerShape(16.dp)

/** Затемнение под шторкой, как в макете (`rgba(6,10,18,.28)`). */
private val ScrimColor = Color(0x47060A12)

/** Шторка заметно плотнее стекла карточек: длинный текст DWD должен читаться на любом небе (макет: стекло на 70 %). */
private const val SHEET_GLASS_SHARE = 0.7f
private const val SHEET_ALPHA = 0.92f

private const val ANIMATION_MS = 300

/**
 * Шторка поверх всего окна (и над панелью вкладок): весь список предупреждений или одно предупреждение. Нажатие на
 * затемнение и крестик закрывают её; «назад» обрабатывает [io.github.dumahuch.pogodaapp.ui.PogodaApp].
 * Предупреждение, которого больше нет в списке (истекло или отменено), закрывает шторку.
 */
@Composable
fun AlertSheetHost(
    sheet: AlertSheet?,
    alerts: List<WeatherAlert>,
    area: String?,
    zone: ZoneId,
    now: Instant,
    onOpen: (AlertSheet) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val open = sheet != null && (sheet !is AlertSheet.One || alerts.any { it.id == sheet.id })
    // Предупреждение исчезло, пока шторка была открыта: закрываем, чтобы «назад» не уходил в пустую шторку.
    LaunchedEffect(open, sheet) { if (!open && sheet != null) onClose() }
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(open, enter = fadeIn(tween(ANIMATION_MS)), exit = fadeOut(tween(ANIMATION_MS))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(ScrimColor)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            )
        }
        AnimatedVisibility(
            open,
            enter = slideInVertically(tween(ANIMATION_MS)) { it } + fadeIn(tween(ANIMATION_MS)),
            exit = slideOutVertically(tween(ANIMATION_MS)) { it } + fadeOut(tween(ANIMATION_MS)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            // Пока шторка уезжает, состояние уже пустое: показываем то, что было открыто.
            val shown = remember { SheetMemory() }
            if (sheet != null) shown.last = sheet
            val current = shown.last
            SheetFrame(title = if (current == AlertSheet.All) stringResource(R.string.alerts_all) else null, onClose = onClose) {
                when (current) {
                    AlertSheet.All -> AllAlerts(alerts, now, zone, onOpen = { onOpen(AlertSheet.One(it.id, fromList = true)) })
                    is AlertSheet.One -> alerts.firstOrNull { it.id == current.id }?.let { AlertDetails(it, area, now, zone) }
                    null -> Unit
                }
            }
        }
    }
}

private class SheetMemory {
    var last: AlertSheet? = null
}

@Composable
private fun SheetFrame(title: String?, onClose: () -> Unit, content: @Composable () -> Unit) {
    val palette = LocalPogodaPalette.current
    val glass = LocalGlass.current
    val base = if (palette.darkInk) Color.White else Color(0xFF0C131F)
    val fill = lerp(base, palette.glass.copy(alpha = 1f), SHEET_GLASS_SHARE).copy(alpha = SHEET_ALPHA)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(12.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight - 48.dp)
                .clip(SheetShape)
                .border(1.dp, palette.glassLine, SheetShape)
                .hazeCardBlur(glass)
                .background(fill, SheetShape)
                // Нажатие по самой шторке не должно закрывать её (оно ушло бы к затемнению под ней).
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, top = 14.dp, end = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title.orEmpty(),
                    style = pogodaText(17f, 700, color = palette.ink),
                    modifier = Modifier.weight(1f).semantics { if (title != null) heading() },
                )
                CloseButton(onClose)
            }
            content()
        }
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val label = stringResource(R.string.alert_close)
    Box(
        modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClose)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        LineIconView(LineIcons.Close, 18.dp, palette.ink2)
    }
}

@Composable
private fun AllAlerts(alerts: List<WeatherAlert>, now: Instant, zone: ZoneId, onOpen: (WeatherAlert) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AlertGap)) {
        alerts.forEach { alert -> AlertRow(alert, now, zone, onClick = { onOpen(alert) }) }
    }
}

/**
 * Одно предупреждение: значок и название явления, шкала уровня, период и район, оригинальный текст DWD (на немецком,
 * при английском интерфейсе английская версия DWD; решение Димы) и источник со временем выпуска.
 */
@Composable
private fun AlertDetails(alert: WeatherAlert, area: String?, now: Instant, zone: ZoneId) {
    val palette = LocalPogodaPalette.current
    val tint = levelColor(alert.level)
    val english = LocalLocale.current.platformLocale.language == "en"
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LineIconView(kindIcon(alert.kind), 32.dp, tint)
        Text(
            alertTitle(alert.kind),
            style = pogodaText(19f, 700, color = palette.ink, lineHeight = 1.2f),
            modifier = Modifier.semantics { heading() },
        )
    }
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
