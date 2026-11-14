package io.github.dumahuch.pogodaapp.map

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Сутки на шкале карты: «Сейчас» (сегодня), «Завтра», «Послезавтра». */
enum class MapDay { Today, Tomorrow, DayAfter }

/** Деление шкалы: большое (час, с подписью) или малое (15 минут, там, где кадры чаще часа). */
data class RulerTick(val time: Instant, val major: Boolean)

/**
 * Время на карте. Шкала одна, сплошная: с сегодняшних 00:00 до конца прогноза ICON-D2 ([modelEnd], около 48 часов
 * вперёд: «Послезавтра» обрезано, а ночью может не начаться, см. [hasFrames]); палец ведёт её через полночь,
 * а кнопки суток только прыгают и подсвечиваются по тому, на каких сутках стоит шкала ([dayOf]).
 * Шаг выбора: около «сейчас» (два часа назад и два вперёд, пока есть радар) 5 минут; прошедшие часы раньше этого
 * по часам; после радара 15 минут, как дождь ICON-D2 ([ModelStep]), до самого конца шкалы ([modelEnd]).
 * Малые деления через 15 минут там, где шаг мельче часа.
 */
object MapTimeline {
    val RadarStep: Duration = Duration.ofMinutes(5)

    /** Насколько вперёд радар DWD даёт прогноз. */
    val RadarAhead: Duration = Duration.ofHours(2)

    /** Сколько назад от «сейчас» шкала идёт мелким шагом. */
    val FineBack: Duration = Duration.ofHours(2)

    val MinorTick: Duration = Duration.ofMinutes(15)

    /** Шаг шкалы на прогнозе после радара: так часто ICON-D2 выкладывает осадки ([IconD2Rain.Step]). */
    val ModelStep: Duration = IconD2Rain.Step

    /** Кадры в этих пределах от выбранного времени подгружаются раньше всех остальных. */
    val FingerZone: Duration = Duration.ofMinutes(90)

    /** Через сколько после начала прогон ICON-D2 с запасом лежит на сервере (обычно через 1 ч 20 мин). */
    private val ModelDelay: Duration = Duration.ofHours(2)

    private val ModelRuns: Duration = Duration.ofHours(3)

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

    private fun dayEnd(day: MapDay, now: Instant, zone: ZoneId): Instant =
        LocalDate.ofInstant(now, zone).plusDays(day.ordinal + 1L).atStartOfDay(zone).toInstant()

    /** Начало и конец всей шкалы: с сегодняшних 00:00 до конца прогноза ICON-D2 ([modelEnd]), но не дальше конца «Послезавтра». */
    fun range(now: Instant, zone: ZoneId): Pair<Instant, Instant> =
        dayStart(MapDay.Today, now, zone) to minOf(dayEnd(MapDay.DayAfter, now, zone), modelEnd(now))

    /**
     * Сутки [day] на шкале: от их 00:00 до следующих 00:00 (полночь уже следующие сутки), но не дальше конца шкалы
     * ([range]). Если сутки начинаются после конца шкалы, начало позже конца (см. [hasFrames]).
     */
    fun dayRange(day: MapDay, now: Instant, zone: ZoneId): Pair<Instant, Instant> =
        dayStart(day, now, zone) to minOf(dayEnd(day, now, zone), range(now, zone).second)

    /** Есть ли на шкале хоть один кадр суток [day]: ночью прогноз ICON-D2 ещё не доходит до «Послезавтра». */
    fun hasFrames(day: MapDay, now: Instant, zone: ZoneId): Boolean =
        !dayStart(day, now, zone).isAfter(range(now, zone).second)

    /** На каких сутках стоит [time]: их кнопка подсвечена. */
    fun dayOf(time: Instant, now: Instant, zone: ZoneId): MapDay =
        MapDay.entries.firstOrNull { time.isBefore(dayEnd(it, now, zone)) } ?: MapDay.DayAfter

    /** Последний момент, на который радар даёт прогноз. */
    fun radarEnd(now: Instant): Instant = anchor(now).plus(RadarAhead)

    /**
     * До какого времени есть дождь ICON-D2 по 15 минут: последний прогон, который уже должен лежать на сервере,
     * плюс [IconD2Files.MAX_LEAD] часов. Дальше дождь только по часам из ICON-EU.
     */
    fun modelEnd(now: Instant): Instant = floorTo(now.minus(ModelDelay), ModelRuns).plus(Duration.ofHours(IconD2Files.MAX_LEAD.toLong()))

    /** Отрезок, где шкала идёт шагом радара. */
    fun fineWindow(now: Instant): Pair<Instant, Instant> = anchor(now).minus(FineBack) to radarEnd(now)

    /** Ближайшее допустимое время к [time] на шкале. */
    fun snap(time: Instant, now: Instant, zone: ZoneId): Instant {
        val (start, end) = range(now, zone)
        val clamped = time.coerceIn(start, end)
        val (fineStart, fineEnd) = fineWindow(now)
        val result = when {
            clamped.isBefore(fineStart) -> roundTo(clamped, Hour)
            !clamped.isAfter(fineEnd) -> roundTo(clamped, RadarStep)
            else -> {
                // После радара шаг 15 минут до конца шкалы; ближайшая к пальцу четверть часа может оказаться ещё на радаре.
                val quarter = roundTo(clamped, ModelStep)
                if (quarter.isAfter(fineEnd)) quarter else fineEnd
            }
        }
        return result.coerceIn(start, end)
    }

    /** Все времена от [from] до [to], на которые шкала может остановиться (см. [snap]). */
    fun frames(from: Instant, to: Instant, now: Instant, zone: ZoneId): List<Instant> {
        val frames = sortedSetOf<Instant>()
        var time = floorTo(from, RadarStep)
        while (!time.isAfter(to)) {
            snap(time, now, zone).let { if (!it.isBefore(from) && !it.isAfter(to)) frames += it }
            time = time.plus(RadarStep)
        }
        return frames.toList()
    }

    /**
     * Что подгружать заранее, пока шкала стоит на сутках [day]: их кадры и ещё [FingerZone] по краям, чтобы
     * за полночь палец уходил на готовые кадры.
     */
    fun warmFrames(day: MapDay, now: Instant, zone: ZoneId): List<Instant> {
        val (start, end) = dayRange(day, now, zone)
        return frames(start.minus(FingerZone), end.plus(FingerZone), now, zone)
    }

    /**
     * Какой кадр из [pending] подгружать следующим. Сначала кадры рядом с шкалой (в пределах [FingerZone] от
     * [focus]: их человек увидит первыми), потом будущее (от «сейчас» вперёд), потом прошлое; внутри группы
     * ближайший к [focus] первым. Очередь пересчитывается при каждом выборе, поэтому она идёт за шкалой.
     */
    fun nextToWarm(pending: Collection<Instant>, focus: Instant, now: Instant): Instant? {
        val anchor = anchor(now)
        fun group(time: Instant): Int = when {
            Duration.between(time, focus).abs() <= FingerZone -> 0
            !time.isBefore(anchor) -> 1
            else -> 2
        }
        return pending.minWithOrNull(
            compareBy<Instant> { group(it) }.thenBy { Duration.between(it, focus).abs() }.thenBy { it },
        )
    }

    /** Деления всей шкалы: каждый час большое, малые через 15 минут там, где шаг мельче часа (радар и ICON-D2). */
    fun ticks(now: Instant, zone: ZoneId): List<RulerTick> {
        val (start, end) = range(now, zone)
        val fineStart = fineWindow(now).first
        val fineEnd = modelEnd(now)
        val result = mutableListOf<RulerTick>()
        var time = start
        while (!time.isAfter(end)) {
            val major = time.atZone(zone).minute == 0
            val fine = !time.isBefore(fineStart) && !time.isAfter(fineEnd)
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
        return snap(target, now, zone)
    }

    /** Кадры кнопки «играть»: сегодня два часа назад и прогноз радара через 15 минут, другие сутки по часам. */
    fun playFrames(day: MapDay, now: Instant, zone: ZoneId): List<Instant> {
        val (start, end) = dayRange(day, now, zone)
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
            // Сутки обрезаны концом шкалы: последний кадр это сам конец; иначе без полуночи следующих суток.
            last = if (end.isBefore(dayEnd(day, now, zone))) end else end.minus(Hour)
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

    /** Дождь на [time] берётся из радара (иначе из модели: ICON-D2 по 15 минут, где её нет, ICON-EU по часам). */
    fun fromRadar(time: Instant, now: Instant): Boolean = !time.isAfter(radarEnd(now))

    /** Кадр радара для [time]: шаг 5 минут. */
    fun radarFrame(time: Instant): Instant = floorTo(time, RadarStep)

    /**
     * Кадр ICON-EU для [time]: слой DWD показывает сумму за час, который кончается в своё время, поэтому
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
