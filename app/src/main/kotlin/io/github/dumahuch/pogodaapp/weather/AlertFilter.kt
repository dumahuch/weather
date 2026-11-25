package io.github.dumahuch.pogodaapp.weather

/**
 * Какие предупреждения DWD сообщать уведомлением (решения Димы 25.11.2026). Непогода и экстремальная непогода (уровни 3 и 4)
 * сообщаются всегда и любого вида, остальное по выбору: не ниже [minLevel] и только выбранные явления [kinds].
 * На «Главной» предупреждения видны все, фильтр касается только уведомлений.
 */
data class AlertFilter(
    val minLevel: Int = DEFAULT_MIN_LEVEL,
    val kinds: Set<AlertKind> = DEFAULT_KINDS,
) {
    fun accepts(alert: WeatherAlert): Boolean {
        val level = alert.level.number
        return level >= LOUD_LEVEL || (level >= minLevel && alert.kind in kinds)
    }

    companion object {
        /** С этого уровня предупреждение сообщается со звуком и всегда, что бы ни было выбрано. */
        const val LOUD_LEVEL = 3

        /** Нижний уровень, который можно выбрать: «от 1» это любые предупреждения выбранных явлений. */
        const val LOWEST_LEVEL = 1

        /** По умолчанию с уровня 2 («выраженная погода»): жёлтые предупреждения DWD сыплются слишком часто. */
        const val DEFAULT_MIN_LEVEL = 2

        /** Явления по умолчанию: те, о которых разумно знать заранее; заморозки, туман, жара, УФ, оттепель и прочее выбираются вручную. */
        val DEFAULT_KINDS: Set<AlertKind> =
            setOf(AlertKind.Thunderstorm, AlertKind.Wind, AlertKind.Rain, AlertKind.Snow, AlertKind.Ice)

        fun encodeKinds(kinds: Set<AlertKind>): String = AlertKind.entries.filter { it in kinds }.joinToString(",") { it.name }

        /** Обратно: `null` (ещё не выбирали) даёт [DEFAULT_KINDS], пустая строка это «ничего не выбрано», незнакомые имена пропускаются. */
        fun decodeKinds(text: String?): Set<AlertKind> {
            if (text == null) return DEFAULT_KINDS
            val names = text.split(',').map { it.trim() }.toSet()
            return AlertKind.entries.filter { it.name in names }.toSet()
        }

        /** Сохранённый уровень; вне допустимых значений (1 или 2) берётся по умолчанию. */
        fun levelOf(text: String?): Int =
            text?.toIntOrNull()?.takeIf { it in LOWEST_LEVEL until LOUD_LEVEL } ?: DEFAULT_MIN_LEVEL
    }
}
