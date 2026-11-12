package io.github.dumahuch.pogodaapp.map

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Сутки на шкале карты: «Сейчас» (сегодня), «Завтра», «Послезавтра». */
enum class MapDay { Today, Tomorrow, DayAfter }

/** Деление шкалы: большое (час, с подписью) или малое (15 минут, только около «сейчас»). */
data class RulerTick(val time: Instant, val major: Boolean)

/**
 * Время на карте. Сегодня шкала идёт с 00:00 до 24:00 (или до конца прогноза радара, если он уходит за полночь);
 * около «сейчас» (два часа назад и два вперёд, пока есть радар с шагом 5 минут) время выбирается с шагом 5 минут,
 * дальше по часам. Завтра и послезавтра только по часам: облака и дождь там модельные, часовые.
 */
object MapTimeline {
    val RadarStep: Duration = Duration.ofMinutes(5)

    /** Насколько вперёд радар DWD даёт прогноз. */
    val RadarAhead: Duration = Duration.ofHours(2)

    /** Сколько назад от «сейчас» шкала идёт мелким шагом. */
    val FineBack: Duration = Duration.ofHours(2)

    val MinorTick: Duration = Duration.ofMinutes(15)

    private val Hour: Duration = Duration.ofHours(1)

    fun floorTo(time: Instant, step: Duration): Instant {
        val seconds = step.seconds
        return Instant.ofEpochSecond(Math.floorDiv(time.epochSecond, seconds) * seconds)
    }

    fun roundTo(time: Instant, step: Duration): Instant = floorTo(time.plus(step.dividedBy(2)), step)

    /** «Сейчас» на шкале: последний кадр радара, не позже настоящего времени. */
    fun anchor(now: Instant): Instant = floorTo(now, RadarStep)

    fun dayStart(day: MapDay, now: Instant, zone: ZoneId): Instant =
        LocalDate.ofInstant(now, zone).plusDays(day.ordinal.toLong()).atStartOfDay(zone).toInstant()

    /** Начало и конец шкалы суток [day]. */
    fun range(day: MapDay, now: Instant, zone: ZoneId): Pair<Instant, Instant> {
        val start = dayStart(day, now, zone)
        val end = LocalDate.ofInstant(start, zone).plusDays(1).atStartOfDay(zone).toInstant()
        if (day != MapDay.Today) return start to end
        val radarEnd = radarEnd(now)
        return start to if (radarEnd.isAfter(end)) radarEnd else end
    }

    /** Последний момент, на который радар даёт прогноз. */
    fun radarEnd(now: Instant): Instant = anchor(now).plus(RadarAhead)

    /** Отрезок, где сегодня шкала идёт шагом радара. */
    fun fineWindow(now: Instant): Pair<Instant, Instant> = anchor(now).minus(FineBack) to radarEnd(now)

    /** Ближайшее допустимое время к [time] на шкале суток [day]. */
    fun snap(time: Instant, day: MapDay, now: Instant, zone: ZoneId): Instant {
        val (start, end) = range(day, now, zone)
        val clamped = time.coerceIn(start, end)
        if (day == MapDay.Today) {
            val (fineStart, fineEnd) = fineWindow(now)
            if (!clamped.isBefore(fineStart) && !clamped.isAfter(fineEnd)) {
                return roundTo(clamped, RadarStep).coerceIn(start, end)
            }
        }
        return roundTo(clamped, Hour).coerceIn(start, end)
    }

    /**
     * Все времена, на которые шкала суток [day] может остановиться (см. [snap]): каждый час, сегодня около «сейчас»
     * ещё и каждые 5 минут. По ним кадры дня грузятся заранее.
     */
    fun dayFrames(day: MapDay, now: Instant, zone: ZoneId): List<Instant> {
        val (start, end) = range(day, now, zone)
        val frames = sortedSetOf<Instant>()
        var time = start
        while (!time.isAfter(end)) {
            frames += snap(time, day, now, zone)
            time = time.plus(Hour)
        }
        if (day == MapDay.Today) {
            val (fineStart, fineEnd) = fineWindow(now)
            var fine = floorTo(fineStart, RadarStep)
            while (!fine.isAfter(fineEnd)) {
                frames += snap(fine, day, now, zone)
                fine = fine.plus(RadarStep)
            }
        }
        return frames.toList()
    }

    /** Деления шкалы суток [day]: каждый час большое, около «сейчас» малые через 15 минут. */
    fun ticks(day: MapDay, now: Instant, zone: ZoneId): List<RulerTick> {
        val (start, end) = range(day, now, zone)
        val (fineStart, fineEnd) = fineWindow(now)
        val result = mutableListOf<RulerTick>()
        var time = start
        while (!time.isAfter(end)) {
            val major = time.atZone(zone).minute == 0
            val fine = day == MapDay.Today && !time.isBefore(fineStart) && !time.isAfter(fineEnd)
            if (major || fine) result += RulerTick(time, major)
            time = time.plus(MinorTick)
        }
        return result
    }

    /**
     * Время после нажатия на сутки [day]: «Сейчас» возвращает к [anchor], другие сутки дают то же время суток,
     * что выбрано сейчас ([current]), округлённое до часа (20:50 → завтра 21:00).
     */
    fun sameTimeOn(day: MapDay, current: Instant, now: Instant, zone: ZoneId): Instant {
        if (day == MapDay.Today) return anchor(now)
        val local = roundTo(current, Hour).atZone(zone).toLocalTime()
        val target = LocalDate.ofInstant(now, zone).plusDays(day.ordinal.toLong()).atTime(local).atZone(zone).toInstant()
        return snap(target, day, now, zone)
    }

    /** Кадры кнопки «играть»: сегодня два часа назад и прогноз радара через 15 минут, другие сутки по часам. */
    fun playFrames(day: MapDay, now: Instant, zone: ZoneId): List<Instant> {
        val (start, end) = range(day, now, zone)
        val step: Duration
        var time: Instant
        val last: Instant
        if (day == MapDay.Today) {
            val (fineStart, fineEnd) = fineWindow(now)
            step = MinorTick
            time = floorTo(fineStart, MinorTick).coerceAtLeast(start)
            last = fineEnd
        } else {
            step = Hour
            time = start
            last = end.minus(Hour)
        }
        val frames = mutableListOf<Instant>()
        while (!time.isAfter(last)) {
            frames += time
            time = time.plus(step)
        }
        return frames
    }

    /** Время позже «сейчас»: на шкале это прогноз (голубым и с пометкой «Прогноз»). */
    fun isForecast(time: Instant, now: Instant): Boolean = time.isAfter(anchor(now))

    /** Дождь на [time] берётся из радара (иначе из модели ICON-EU, по часам). */
    fun fromRadar(time: Instant, now: Instant): Boolean = !time.isAfter(radarEnd(now))

    /** Кадр радара для [time]: шаг 5 минут. */
    fun radarFrame(time: Instant): Instant = floorTo(time, RadarStep)

    /**
     * Кадр модели осадков для [time]: слой DWD показывает сумму за час, который кончается в своё время, поэтому
     * для 15:00 берётся кадр 16:00 (дождь с 15 до 16).
     */
    fun modelRainFrame(time: Instant): Instant = floorTo(time, Hour).plus(Hour)

    /** Час облаков для [time]: облачность модели мгновенная, берётся ближайший час. */
    fun cloudHour(time: Instant): Instant = roundTo(time, Hour)

    private fun Instant.coerceIn(start: Instant, end: Instant): Instant = when {
        isBefore(start) -> start
        isAfter(end) -> end
        else -> this
    }

    private fun Instant.coerceAtLeast(start: Instant): Instant = if (isBefore(start)) start else this
}
