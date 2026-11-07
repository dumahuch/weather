package io.github.dumahuch.pogodaapp.location

import io.github.dumahuch.pogodaapp.settings.SettingsStorage

/**
 * Окно «Точное / Приблизительное» показывается один раз при первом запуске версии с точным местом (решение Димы 07.11.2026).
 * Нужно тем, у кого уже выдано только приблизительное разрешение: у них системное окно само не появится. Новым людям
 * его показывает обычный запрос доступа. Что человек выбрал, приложение принимает: повторно окно не показывается.
 */
class PreciseLocationPrompt(private val storage: SettingsStorage) {

    /** Истина, если окно ещё не показывали, приблизительное место уже выдано, а точное нет. */
    fun shouldAsk(hasCoarse: Boolean, hasFine: Boolean): Boolean =
        !asked() && hasCoarse && !hasFine

    /** Запоминает, что окно показано (любым путём: обычным запросом или этим). */
    fun markAsked() {
        if (!asked()) storage.write(KEY, "1")
    }

    private fun asked() = storage.read(KEY) == "1"

    internal companion object {
        const val KEY = "precise_location_asked"
    }
}
