package io.github.dumahuch.pogodaapp.data

import java.util.Locale

/**
 * Координаты в адресе запроса: три знака после запятой (около 100 м). Решение Димы 07.11.2026: «чем точнее, тем лучше»;
 * раньше было два знака (около 1 км). Точка всегда через «.», даже при немецком языке телефона.
 */
internal object Coordinates {
    fun param(degrees: Double): String = String.format(Locale.ROOT, "%.3f", degrees)
}
