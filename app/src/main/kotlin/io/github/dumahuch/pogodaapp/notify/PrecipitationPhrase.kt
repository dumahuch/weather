package io.github.dumahuch.pogodaapp.notify

import android.content.Context
import android.icu.text.ListFormatter
import io.github.dumahuch.pogodaapp.R
import io.github.dumahuch.pogodaapp.forecast.PrecipitationCaption
import java.util.Locale

/** «Осадки днём», «Без осадков», «Осадки завтра утром»: общая фраза для уведомления о погоде и виджета. */
internal fun precipitationPhrase(context: Context, caption: PrecipitationCaption, locale: Locale): String =
    when (caption) {
        PrecipitationCaption.None -> context.getString(R.string.precip_today_none)
        PrecipitationCaption.TomorrowMorning -> context.getString(R.string.notify_precip_tomorrow_morning)
        is PrecipitationCaption.Today -> if (caption.allDay) {
            context.getString(R.string.precip_today_all_day)
        } else {
            val names = context.resources.getStringArray(R.array.precip_day_parts)
            val list = ListFormatter.getInstance(locale).format(caption.parts.map { names[it.ordinal] })
            context.getString(R.string.precip_today_parts, list)
        }
    }
