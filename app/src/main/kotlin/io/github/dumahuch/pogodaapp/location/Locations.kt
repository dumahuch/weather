package io.github.dumahuch.pogodaapp.location

import android.content.Context

/** Источник места в рабочей сборке: только настоящее местоположение телефона. */
object Locations {
    fun create(context: Context): LocationSource = AndroidLocationSource(context)
}
