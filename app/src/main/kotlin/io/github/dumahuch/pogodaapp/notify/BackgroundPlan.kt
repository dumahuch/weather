package io.github.dumahuch.pogodaapp.notify

import io.github.dumahuch.pogodaapp.settings.NotificationPrefs

/**
 * Нужна ли телефону фоновая работа (аудит У1, 0.8.65). Если Android не разрешает уведомления (человек нажал «Не
 * разрешать» или выключил их в системе), выключатели в настройках всё равно горят, но показывать нечего: задача не
 * качает прогноз, предупреждения, радар и пыльцу впустую и живёт только ради виджета. Чистая логика, без Android:
 * [BackgroundPlanTest].
 */
object BackgroundPlan {
    /** Периодическая задача нужна, если уведомления разрешены и хоть одно включено, либо на рабочем столе есть виджет. */
    fun periodicJobNeeded(prefs: NotificationPrefs, allowed: Boolean, hasWidgets: Boolean): Boolean =
        (prefs.background && allowed) || hasWidgets

    /** Вечерняя сводка ставится только при включённом выключателе и разрешённых уведомлениях. */
    fun summaryJobNeeded(prefs: NotificationPrefs, allowed: Boolean): Boolean = prefs.summary && allowed
}
