package io.github.dumahuch.pogodaapp.notify

import io.github.dumahuch.pogodaapp.forecast.continuationCase
import io.github.dumahuch.pogodaapp.settings.NotificationPrefs
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Канал уведомления о погоде. [StatusBar] обычной важности без звука и вибрации: только у такого Android ставит значок
 * в строку состояния и показывает уведомление на заблокированном экране. [Shade] наименьшей важности: запись лежит внизу
 * шторки, значка в строке состояния нет.
 */
enum class NoticeChannel(val id: String) {
    StatusBar("weather_status"),
    Shade("weather_shade"),
}

/** Решения уведомления о погоде без Android (0.7.0): какой канал, свежи ли данные, давно ли определено место. */
object WeatherNotice {
    /** Место считается давним, если приложение определяло его больше суток назад (решение Димы 07.11.2026). */
    val STALE_PLACE: Duration = Duration.ofHours(24)

    /** Сохранённый приложением прогноз годится для уведомления без нового запроса, пока ему не больше часа. */
    val FRESH_CACHE: Duration = Duration.ofHours(1)

    /** Часы телефона могли уйти назад: запись «из будущего» больше чем на 5 минут считается несвежей. */
    private val CLOCK_SLACK: Duration = Duration.ofMinutes(5)

    /** Канал по выключателям; `null`, если уведомление не нужно. Строка состояния важнее: при ней значок с числом. */
    fun channelFor(prefs: NotificationPrefs): NoticeChannel? = when {
        prefs.inStatusBar -> NoticeChannel.StatusBar
        prefs.inShade -> NoticeChannel.Shade
        else -> null
    }

    /** Дата, с которой место не обновлялось, если это было больше суток назад; иначе `null` (пометки нет). */
    fun placeSince(placeAt: Instant, now: Instant, zone: ZoneId): LocalDate? =
        if (Duration.between(placeAt, now) > STALE_PLACE) placeAt.atZone(zone).toLocalDate() else null

    /** Истина, если прогноз сохранён не раньше часа назад (и не «из будущего»). */
    fun fresh(savedAt: Instant, now: Instant): Boolean =
        !savedAt.isAfter(now.plus(CLOCK_SLACK)) && Duration.between(savedAt, now) <= FRESH_CACHE

    /**
     * Подпись осадков как продолжение строки после « · »: в русском и английском со строчной («осадки днём»), в немецком
     * без изменений, потому что фраза начинается с существительного («Niederschlag abends»).
     */
    fun precipitationContinuation(phrase: String, locale: Locale): String =
        if (locale.language == "de") phrase else continuationCase(phrase, locale)
}
