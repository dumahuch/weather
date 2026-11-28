package io.github.dumahuch.pogodaapp

/**
 * Номера всех фоновых задач системы в одном месте: у JobScheduler номер общий на всё приложение, а задача с уже занятым
 * номером молча заменяет прежнюю (так очистка кадров карты когда-то стирала вечернюю сводку). Новый номер берут здесь
 * и вносят в [ALL]; тест следит, чтобы номера не повторялись.
 */
object JobIds {
    const val WEATHER_PERIODIC = 7101
    const val WEATHER_NOW = 7102

    /** Две пары номеров: задача, которая идёт сейчас, ставит следующую под другим номером (тот же номер остановил бы её саму). */
    const val SUMMARY_A = 7103
    const val SUMMARY_B = 7104
    const val MAP_PRELOAD_A = 7105
    const val MAP_PRELOAD_B = 7106
    const val UPDATE_CHECK = 7107
    const val MAP_CLEANUP = 7108

    val ALL: List<Int> = listOf(
        WEATHER_PERIODIC, WEATHER_NOW, SUMMARY_A, SUMMARY_B, MAP_PRELOAD_A, MAP_PRELOAD_B, UPDATE_CHECK, MAP_CLEANUP,
    )
}
