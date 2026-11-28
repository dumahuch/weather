package io.github.dumahuch.pogodaapp

/**
 * Номера всех фоновых задач системы в одном месте: у JobScheduler номер общий на всё приложение, а задача с уже занятым
 * номером молча заменяет прежнюю (так очистка кадров карты когда-то стирала вечернюю сводку). Новый номер берут здесь через
 * [id]: он сам попадает в [ALL], а тест следит, чтобы номера не повторялись.
 */
object JobIds {
    private val taken = mutableListOf<Int>()

    private fun id(number: Int): Int {
        taken += number
        return number
    }

    val WEATHER_PERIODIC = id(7101)
    val WEATHER_NOW = id(7102)

    /** Две пары номеров: задача, которая идёт сейчас, ставит следующую под другим номером (тот же номер остановил бы её саму). */
    val SUMMARY_A = id(7103)
    val SUMMARY_B = id(7104)
    val MAP_PRELOAD_A = id(7105)
    val MAP_PRELOAD_B = id(7106)
    val UPDATE_CHECK = id(7107)
    val MAP_CLEANUP = id(7108)

    /** Все занятые номера (по порядку объявления). */
    val ALL: List<Int> get() = taken.toList()
}
