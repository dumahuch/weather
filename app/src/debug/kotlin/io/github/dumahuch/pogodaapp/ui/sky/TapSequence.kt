package io.github.dumahuch.pogodaapp.ui.sky

/**
 * Считает нажатия подряд: [needed] нажатий, между соседними не больше [maxGapMillis].
 * Так открывается скрытый экран проверки; случайно семь раз подряд на подпись версии не нажать.
 */
internal class TapSequence(private val needed: Int = 7, private val maxGapMillis: Long = 1500) {
    private var count = 0
    private var lastAt = 0L

    /** Учитывает нажатие в момент [nowMillis]. Возвращает true, когда набралось нужное число (счёт тогда обнуляется). */
    fun tap(nowMillis: Long): Boolean {
        count = if (count > 0 && nowMillis - lastAt <= maxGapMillis) count + 1 else 1
        lastAt = nowMillis
        if (count < needed) return false
        count = 0
        return true
    }
}
