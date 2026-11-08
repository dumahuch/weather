package io.github.dumahuch.pogodaapp.weather

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Уведомление о пыльце (0.7.0, шаг 4; решения Димы 08.11.2026): раз в день, если на сегодня у выбранного вида индекс DWD не ниже
 * «от средней до высокой» («2-3»). Чистая логика без Android; сеть и показ в `PollenNotifier`.
 */
object PollenNotice {
    /** Порог: «2-3» по шкале DWD, то есть от средней нагрузки к высокой и выше. */
    val THRESHOLD = PollenLevel(5)

    /** Виды, о которых сообщается, пока человек не выбрал свои: главные аллергены (как в плитке «Пыльца»). */
    val DEFAULT_KINDS: Set<PollenKind> = setOf(PollenKind.Birch, PollenKind.Grass, PollenKind.Mugwort, PollenKind.Ragweed)

    /** Уведомление приходит с 7 до 21 часа по местному времени, чтобы ночной запуск задачи не будил звуком. */
    val FROM: LocalTime = LocalTime.of(7, 0)
    val UNTIL: LocalTime = LocalTime.of(21, 0)

    /** Файл DWD скачивается не чаще чем раз в три часа (он выходит раз в сутки). */
    val RECHECK: Duration = Duration.ofHours(3)

    /** Нужно ли сейчас смотреть файл: в разрешённые часы, ещё не сообщали сегодня, прошлая проверка давно (или часы ушли назад). */
    fun due(now: Instant, zone: ZoneId, checkedAt: Instant?, notifiedOn: LocalDate?): Boolean {
        val local = now.atZone(zone)
        if (local.toLocalTime().isBefore(FROM) || !local.toLocalTime().isBefore(UNTIL)) return false
        if (notifiedOn == local.toLocalDate()) return false
        return checkedAt == null || checkedAt.isAfter(now) || Duration.between(checkedAt, now) >= RECHECK
    }

    /** Выбранные виды с нагрузкой не ниже порога на [date]: сильнее раньше, при равенстве в порядке главных аллергенов. */
    fun hits(forecast: PollenForecast, date: LocalDate, selected: Set<PollenKind>): List<Pair<PollenKind, PollenLevel>> {
        val day = forecast.on(date) ?: return emptyList()
        return PollenForecast.DISPLAY_PRIORITY
            .filter { it in selected }
            .mapNotNull { kind -> day[kind]?.takeIf { it >= THRESHOLD }?.let { kind to it } }
            .sortedByDescending { it.second }
    }

    /** Выбранные виды для хранения: имена через запятую в порядке перечисления. */
    fun encodeKinds(kinds: Set<PollenKind>): String = PollenKind.entries.filter { it in kinds }.joinToString(",") { it.name }

    /** Обратно: `null` (ещё не выбирали) даёт [DEFAULT_KINDS], пустая строка это «ничего не выбрано», незнакомые имена пропускаются. */
    fun decodeKinds(text: String?): Set<PollenKind> {
        if (text == null) return DEFAULT_KINDS
        val names = text.split(',').map { it.trim() }.toSet()
        return PollenKind.entries.filter { it.name in names }.toSet()
    }
}
