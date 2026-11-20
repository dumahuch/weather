package io.github.dumahuch.pogodaapp.ui.metric

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.RangeSpan
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.components.LineIcons
import io.github.dumahuch.pogodaapp.ui.components.LocalGlass
import io.github.dumahuch.pogodaapp.ui.components.glassCard
import io.github.dumahuch.pogodaapp.ui.forecast.dayName
import io.github.dumahuch.pogodaapp.ui.forecast.shortDate
import io.github.dumahuch.pogodaapp.ui.pogodaText
import java.time.LocalDate

/**
 * Строка списка «7 дней»: значения слева и справа от полоски, полоска на общей шкале (пусто: полоски нет, например
 * день без осадков), [direction] стрелка ветра, [wet] капля (пусто: значка нет).
 */
internal class MetricDayRow(
    val date: LocalDate,
    val left: String,
    val right: String,
    val span: RangeSpan?,
    val direction: Int? = null,
    val wet: Boolean? = null,
)

/**
 * Список дней внизу страницы подробностей, строками как на вкладке «Прогноз»: день и дата, значок, «от … полоска … до».
 * Нажатие на строку показывает этот день в карточке и ленте выше; выбранная строка подсвечена.
 */
@Composable
internal fun MetricDaysCard(
    rows: List<MetricDayRow>,
    note: String,
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPogodaPalette.current
    val locale = LocalLocale.current.platformLocale
    val dayNames = stringArrayResource(R.array.days_short).toList()
    val todayName = stringResource(R.string.today)
    val withGlyph = rows.any { it.direction != null || it.wet != null }
    Column(
        modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(24.dp), LocalGlass.current)
            .padding(start = 10.dp, top = 14.dp, end = 10.dp, bottom = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.forecast_7_days), style = pogodaText(13f, 700), modifier = Modifier.alignByBaseline())
            Text(note, style = pogodaText(12f, 400, color = palette.ink2), modifier = Modifier.alignByBaseline())
        }
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(1.dp).background(palette.glassLine))
            }
            val isSelected = row.date == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .padding(vertical = 2.dp)
                    .then(if (isSelected) Modifier.background(palette.accentSoft, RoundedCornerShape(14.dp)) else Modifier)
                    .clickable(role = Role.Button) { onSelect(row.date) }
                    .semantics(mergeDescendants = true) { this.selected = isSelected }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Column(Modifier.width(DayColumnWidth)) {
                    Text(
                        if (row.date == today) todayName else dayName(row.date, dayNames),
                        style = pogodaText(15f, 700, lineHeight = 1.15f),
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        shortDate(row.date, locale),
                        style = pogodaText(11f, 500, color = palette.ink2, lineHeight = 1.15f),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                if (withGlyph) {
                    Box(Modifier.width(26.dp), contentAlignment = Alignment.Center) {
                        when {
                            row.direction != null -> WindArrow(row.direction, 22.dp, palette.ink)
                            row.wet != null -> LineIconView(
                                LineIcons.Drop,
                                20.dp,
                                if (row.wet) palette.bar else palette.ink.copy(alpha = 0.35f),
                                strokeWidth = 1.8f,
                            )
                        }
                    }
                }
                Text(
                    row.left,
                    style = pogodaText(15f, 500, color = palette.ink2, tabular = true),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(ValueColumnWidth),
                )
                SpanBar(row.span, Modifier.weight(1f))
                Text(
                    row.right,
                    style = pogodaText(15f, 700, tabular = true),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(ValueColumnWidth),
                )
            }
        }
    }
}

private val DayColumnWidth = 62.dp
private val ValueColumnWidth = 44.dp

/** Полоска «от и до» на общей шкале, цветом акцента; без значения только дорожка. */
@Composable
private fun SpanBar(span: RangeSpan?, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    Canvas(modifier.height(5.dp)) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(palette.glassLine, size = size, cornerRadius = radius)
        if (span != null) {
            drawRoundRect(
                palette.accent,
                topLeft = Offset(span.start * size.width, 0f),
                size = Size(span.width * size.width, size.height),
                cornerRadius = radius,
            )
        }
    }
}
