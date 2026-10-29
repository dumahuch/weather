package io.github.dumahuch.pogodaapp.weather

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Восемь видов пыльцы, которые даёт индекс опасности пыльцы DWD (Pollenflug-Gefahrenindex). Порядок объявления
 * совпадает с порядком названий в ресурсах (`pollen_kinds`) и менять его нельзя.
 */
enum class PollenKind(val dwdKey: String) {
    Hazel("Hasel"),
    Alder("Erle"),
    Ash("Esche"),
    Birch("Birke"),
    Grass("Graeser"),
    Rye("Roggen"),
    Mugwort("Beifuss"),
    Ragweed("Ambrosia"),
}

/**
 * Нагрузка пыльцой по шкале DWD: 0 нет, 1 слабая, 2 средняя, 3 высокая и промежуточные ступени «0-1», «1-2», «2-3».
 * Хранится в половинках ступени, от 0 до 6, чтобы всё считалось целыми числами.
 */
@JvmInline
value class PollenLevel(val halves: Int) : Comparable<PollenLevel> {
    init {
        require(halves in 0..MAX_HALVES) { "Ступень пыльцы вне шкалы: $halves" }
    }

    override fun compareTo(other: PollenLevel): Int = halves.compareTo(other.halves)

    /** Целая ступень, до которой нагрузка дошла полностью (0..3): для подписи и для заполненных точек. */
    val whole: Int get() = halves / 2

    /** Истина для промежуточной ступени («0-1», «1-2», «2-3»). */
    val isBetween: Boolean get() = halves % 2 == 1

    companion object {
        const val MAX_HALVES = 6

        /** Разбор записи DWD: «0», «0-1», «1», «1-2», «2», «2-3», «3». Остальное (в том числе «-1», «нет данных») даёт null. */
        fun parse(text: String?): PollenLevel? = when (text?.trim()) {
            "0" -> PollenLevel(0)
            "0-1" -> PollenLevel(1)
            "1" -> PollenLevel(2)
            "1-2" -> PollenLevel(3)
            "2" -> PollenLevel(4)
            "2-3" -> PollenLevel(5)
            "3" -> PollenLevel(6)
            else -> null
        }
    }
}

/**
 * Прогноз пыльцы DWD для одного из 27 районов: уровни на сегодня, завтра и послезавтра по каждому виду.
 * «Сегодня» в файле это день выпуска [issued] (файл выходит раз в сутки около 11 часов по местному времени),
 * поэтому утром следующего дня до нового выпуска «завтра» из файла и есть сегодняшний день.
 * [regionKey] это `partregion_id`, а у районов без частей `region_id` (так DWD нумерует 27 районов).
 */
data class PollenForecast(
    val regionKey: Int,
    val issued: LocalDate,
    private val levels: Map<PollenKind, List<PollenLevel?>>,
) {
    /** Уровни на [date]; пусто, если файл не охватывает этот день (старше выпуска или дальше, чем на три дня). */
    fun on(date: LocalDate): Map<PollenKind, PollenLevel>? {
        val offset = ChronoUnit.DAYS.between(issued, date).toInt()
        if (offset !in 0 until DAYS_COVERED) return null
        val result = LinkedHashMap<PollenKind, PollenLevel>()
        for (kind in PollenKind.entries) {
            levels[kind]?.getOrNull(offset)?.let { result[kind] = it }
        }
        return result.takeIf { it.isNotEmpty() }
    }

    /**
     * Виды для плитки «Пыльца» на [date]: [count] видов с наибольшей нагрузкой, при равенстве в порядке
     * [DISPLAY_PRIORITY]. Когда нагрузки нет нигде, это четыре главных аллергена (берёза, злаки, полынь, амброзия),
     * как в макете. Пусто, если на этот день данных нет.
     */
    fun shown(date: LocalDate, count: Int = SHOWN_KINDS): List<Pair<PollenKind, PollenLevel>>? {
        val day = on(date) ?: return null
        return DISPLAY_PRIORITY.mapNotNull { kind -> day[kind]?.let { kind to it } }
            .sortedByDescending { it.second }
            .take(count)
    }

    companion object {
        /** За сколько дней вперёд даёт уровни файл DWD: сегодня, завтра, послезавтра. */
        const val DAYS_COVERED = 3

        /** Сколько видов показывает плитка. */
        const val SHOWN_KINDS = 4

        /** Порядок при равной нагрузке: сначала самые частые аллергены. */
        val DISPLAY_PRIORITY: List<PollenKind> = listOf(
            PollenKind.Birch,
            PollenKind.Grass,
            PollenKind.Mugwort,
            PollenKind.Ragweed,
            PollenKind.Alder,
            PollenKind.Hazel,
            PollenKind.Ash,
            PollenKind.Rye,
        )
    }
}
