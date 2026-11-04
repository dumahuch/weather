package io.github.dumahuch.pogodaapp.home

/**
 * Как поделить свободное место «Главной» между предупреждениями и ростом верхнего блока (значок и температура).
 * Решение Димы 30.10: предупреждения занимают место от строк под температурой до ленты «По часам», самые серьёзные
 * сверху, что не влезло, уходит под строку «Ещё N»; верхний блок растёт на оставшееся свободное место, и чем больше
 * предупреждений, тем он меньше, но не меньше прежнего размера (0.5.4). Размер не самоцель: блок забирает только
 * часть свободного места ([GROW_SHARE]) и вырастает не больше чем до [MAX_SCALE].
 *
 * Все величины в пикселях. [free] это свободная высота при прежнем размере блока и без предупреждений, вместе с двумя
 * наименьшими промежутками [edge] сверху и снизу (в 0.5.4 она делилась поровну над и под блоком).
 */
internal object HeroFit {

    /** Доля свободного места (после предупреждений), которую забирает рост блока. */
    const val GROW_SHARE = 0.4f

    /** Наибольший размер блока относительно прежнего. */
    const val MAX_SCALE = 1.23f

    /** Высоты частей списка предупреждений. [lead] промежуток над списком, [gap] между строками. */
    class Rows(val row: Float, val more: Float, val gap: Float, val lead: Float) {
        /** Высота списка из [shown] строк и, если [more], строки «Ещё N». */
        fun height(shown: Int, more: Boolean): Float {
            if (shown <= 0 && !more) return 0f
            val rows = shown * row + (shown - 1).coerceAtLeast(0) * gap
            return lead + rows + if (more) gap + this.more else 0f
        }
    }

    /**
     * [shown] сколько предупреждений видно, [more] нужна ли строка «Ещё N» (N = всего − [shown]), [scale] размер блока,
     * [alerts] высота списка, [top] промежуток над блоком (под ним всё остальное свободное место).
     */
    data class Plan(val shown: Int, val more: Boolean, val scale: Float, val alerts: Float, val top: Float)

    fun plan(free: Float, count: Int, rows: Rows, edge: Float, heroBase: Float): Plan {
        val room = free - 2 * edge
        val (shown, more) = fit(count, rows, room)
        val alerts = rows.height(shown, more)
        val growth = if (heroBase <= 0f) 0f else ((room - alerts) * GROW_SHARE).coerceIn(0f, (MAX_SCALE - 1f) * heroBase)
        // Блок растёт больше вверх, чем вниз (две трети и треть): строки под температурой почти не сдвигаются и остаются
        // на подложке, рассчитанной под небо. Предупреждения сначала занимают место под блоком, потом над ним.
        val wanted = free / 2 - growth * 2 / 3
        val most = free - alerts - growth - edge
        val top = wanted.coerceAtMost(most).coerceAtLeast(edge)
        val scale = if (heroBase <= 0f) 1f else 1f + growth / heroBase
        return Plan(shown, more, scale, alerts, top)
    }

    /** Все предупреждения, если влезают; иначе столько, сколько влезет вместе со строкой «Ещё N», но не меньше одного. */
    private fun fit(count: Int, rows: Rows, room: Float): Pair<Int, Boolean> {
        if (count <= 0) return 0 to false
        if (rows.height(count, more = false) <= room) return count to false
        var shown = count - 1
        while (shown > 1 && rows.height(shown, more = true) > room) shown--
        return if (shown >= 1) shown to true else 1 to false
    }
}
