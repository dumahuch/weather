package io.github.dumahuch.pogodaapp.home

/**
 * Как поделить свободное место «Главной» между предупреждениями и размером верхнего блока (значок и температура).
 * Решение Димы 30.10 и 04.11: предупреждения занимают место от строк под температурой до ленты «По часам», самые
 * серьёзные сверху; видны до [MAX_SHOWN] плашек, остальные уходят под строку «Ещё N». Размер блока сначала считается без
 * предупреждений: рост берёт часть свободного места ([GROW_SHARE]) и не больше чем до [MAX_SCALE] (с step-40 это 1,7, а
 * на узком экране или при крупном шрифте ещё меньше: предел по ширине приходит в [plan] как `maxScale`). Плашки не
 * уменьшают блок, а сдвигают его вверх (решение Димы 07.11 вечером); блок отдаёт место, только когда плашки не помещаются
 * даже после сдвига до верхнего края: тогда он растёт меньше или сжимается столько, сколько нужно (нижний предел
 * только технический, [MIN_SCALE]). Остаток места после роста делится поровну над блоком и под ним (step-40).
 *
 * Все величины в пикселях. [free] это свободная высота при прежнем размере блока и без предупреждений, вместе с двумя
 * наименьшими промежутками [edge] сверху и снизу. Масштаб касается только строки цифр ([plan], `heroBase`): место
 * над цифрами фиксированное и не растёт.
 */
internal object HeroFit {

    /** Доля свободного места (без предупреждений), которую забирает рост блока. */
    const val GROW_SHARE = 0.6f

    /** Наибольший размер блока относительно прежнего. */
    const val MAX_SCALE = 1.7f

    /** Наименьший размер блока относительно прежнего: технический предел, чтобы значок и цифры не пропали совсем. */
    const val MIN_SCALE = 0.5f

    /** Сколько плашек предупреждений видно на «Главной», остальные под строкой «Ещё N». */
    const val MAX_SHOWN = 3

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

    /**
     * [heroBase] высота той части блока, которая растёт с масштабом (строка цифр при обычном размере), [maxScale]
     * наибольший масштаб (не больше [MAX_SCALE]: узкий экран и крупный шрифт ограничивают его по ширине).
     */
    fun plan(free: Float, count: Int, rows: Rows, edge: Float, heroBase: Float, maxScale: Float = MAX_SCALE): Plan {
        val room = free - 2 * edge
        // Сколько блок может отдать, сжимаясь до MIN_SCALE: плашки помещаются, если хватает места вместе с этим запасом.
        val shrinkable = if (heroBase <= 0f) 0f else (1f - MIN_SCALE) * heroBase
        val (shown, more) = fit(count, rows, room + shrinkable)
        val alerts = rows.height(shown, more)
        val spare = room - alerts
        // Рост, который блок взял бы без предупреждений; плашки его не отнимают, пока всё помещается.
        val wantedGrowth = (room * GROW_SHARE).coerceAtMost((maxScale.coerceAtMost(MAX_SCALE) - 1f) * heroBase).coerceAtLeast(0f)
        val growth = when {
            heroBase <= 0f -> 0f
            spare >= 0f -> wantedGrowth.coerceAtMost(spare)
            else -> spare.coerceAtLeast(-shrinkable)
        }
        // Остаток места после роста делится поровну над блоком и под ним (подложки теперь привязаны к блоку и сдвигаются
        // вместе с ним). Предупреждения сначала занимают место под блоком, потом над ним.
        val wanted = (free - growth) / 2
        val most = free - alerts - growth - edge
        val top = wanted.coerceAtMost(most).coerceAtLeast(edge)
        val scale = if (heroBase <= 0f) 1f else 1f + growth / heroBase
        return Plan(shown, more, scale, alerts, top)
    }

    /**
     * Не больше [MAX_SHOWN] предупреждений; если вместе со строкой «Ещё N» не влезают, меньше, но не меньше одного.
     * [room] включает всё, что можно получить, сжав блок до [MIN_SCALE].
     */
    private fun fit(count: Int, rows: Rows, room: Float): Pair<Int, Boolean> {
        if (count <= 0) return 0 to false
        var shown = minOf(count, MAX_SHOWN)
        while (shown > 1 && rows.height(shown, more = count > shown) > room) shown--
        return shown to (count > shown)
    }
}
