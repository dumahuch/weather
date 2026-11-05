package io.github.dumahuch.pogodaapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.PrecipitationCaption
import io.github.dumahuch.pogodaapp.ui.LocalPogodaPalette
import io.github.dumahuch.pogodaapp.ui.QuietInk
import io.github.dumahuch.pogodaapp.ui.components.LineIcon
import io.github.dumahuch.pogodaapp.ui.components.LineIconView
import io.github.dumahuch.pogodaapp.ui.pogodaText

// Строка под температурой на «Главной»: значок и подпись рядом для влажности, ветра и осадков.

/** Подпись осадков: части суток через запятую и «и» по правилам языка («Осадки утром и вечером»), «Осадки весь день», «Завтра утром» или «Без осадков». */
@Composable
internal fun precipitationText(caption: PrecipitationCaption): String = when (caption) {
    PrecipitationCaption.None -> stringResource(R.string.precip_today_none)
    PrecipitationCaption.TomorrowMorning -> stringResource(R.string.precip_tomorrow_morning)
    is PrecipitationCaption.Today -> if (caption.allDay) {
        stringResource(R.string.precip_today_all_day)
    } else {
        val names = stringArrayResource(R.array.precip_day_parts)
        val locale = LocalLocale.current.platformLocale
        val list = android.icu.text.ListFormatter.getInstance(locale).format(caption.parts.map { names[it.ordinal] })
        stringResource(R.string.precip_today_parts, list)
    }
}

/**
 * Короткая подпись осадков для строки со значком: «Днём», «Утром и вечером», «Весь день», «Завтра утром», «Нет».
 * Слова «Осадки» нет: его заменяет значок облака с дождём; полная фраза остаётся в описании для чтения с экрана.
 */
@Composable
internal fun precipitationShortText(caption: PrecipitationCaption): String = when (caption) {
    PrecipitationCaption.None -> stringResource(R.string.precip_short_none)
    PrecipitationCaption.TomorrowMorning -> stringResource(R.string.precip_tomorrow_morning)
    is PrecipitationCaption.Today -> if (caption.allDay) {
        stringResource(R.string.precip_short_all_day)
    } else {
        val names = stringArrayResource(R.array.precip_day_parts)
        val locale = LocalLocale.current.platformLocale
        android.icu.text.ListFormatter.getInstance(locale).format(caption.parts.map { names[it.ordinal] })
            .replaceFirstChar { it.titlecase(locale) }
    }
}

/**
 * Один показатель строки под температурой: значок и подпись рядом. [description] читает экран вместо подписи (полная фраза
 * со словом: «Влажность 88 %»). [faded]: значок ослаблен, когда показывать нечего («Нет» у осадков). [wide]: подпись из слов,
 * ей отдаётся всё оставшееся место, и она переносится на вторую строку, если не помещается.
 */
internal class Caption(
    val icon: LineIcon,
    val text: String,
    val description: String,
    val faded: Boolean = false,
    val wide: Boolean = false,
)

/**
 * Одна строка из трёх показателей (влажность, ветер, осадки до конца дня): у каждого значок слева от цифры или слова,
 * между ними тонкие вертикальные черты. Значок и цифра рядом позволили сделать их крупнее прежних мелких подписей
 * (цифра 17 sp, значок 18 dp). Цвет приглушён ([QuietInk]): это второстепенные данные, контраст не выше нужного.
 */
@Composable
internal fun CaptionRow(items: List<Caption>, modifier: Modifier = Modifier) {
    val palette = LocalPogodaPalette.current
    val text = QuietInk.text(palette.ink, palette.infoBackdrop)
    val fadedIcon = QuietInk.fadedIcon(palette.ink, palette.infoBackdrop)
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                Box(Modifier.fillMaxHeight().width(1.dp).background(text.copy(alpha = 0.3f)))
            }
            Row(
                (if (item.wide) Modifier.weight(1f, fill = false) else Modifier)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clearAndSetSemantics { contentDescription = item.description },
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineIconView(item.icon, 18.dp, if (item.faded) fadedIcon else text, strokeWidth = 1.7f)
                Text(
                    item.text,
                    style = if (item.wide) {
                        pogodaText(14f, 400, color = text, lineHeight = 1.2f)
                    } else {
                        pogodaText(17f, 400, color = text, tabular = true)
                    },
                    textAlign = TextAlign.Center,
                    maxLines = if (item.wide) 2 else 1,
                )
            }
        }
    }
}
