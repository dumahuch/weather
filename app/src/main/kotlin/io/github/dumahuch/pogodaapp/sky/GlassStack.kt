package io.github.dumahuch.pogodaapp.sky

import kotlin.math.max

/**
 * Стёкла карточек одного экрана, где лежат друг под другом. Вода, дошедшая до низа стекла, не пропадает: если прямо под
 * ним (через небольшой просвет) есть другая карточка, капля падает на её верхнюю кромку и стекает дальше. Положения
 * ставят сами карточки в пикселях окна, радиусы и расстояния для стекла считаются в dp.
 */
internal class GlassStack {

    /** Одна карточка: где она в окне (левый верх, плотность экрана; размер у её стекла) и её стекло; пока стекла нет, воду не принимает. */
    class Pane {
        var glass: GlassRain? = null
        var left = 0.0
        var top = 0.0
        var density = 1.0
    }

    private val panes = ArrayList<Pane>()

    fun add(pane: Pane) {
        if (pane !in panes) panes += pane
    }

    fun remove(pane: Pane) {
        panes -= pane
    }

    /**
     * Вода радиуса [r] (dp) в точке [x] (dp) нижней кромки карточки [from]: отдаёт её ближайшей карточке снизу, если
     * просвет не больше [MAX_GAP_DP], и возвращает true. Карточка рядом, выше или с другой стороны от точки воду не берёт.
     */
    fun spill(from: Pane, x: Double, r: Double): Boolean {
        val bottom = from.top + (from.glass?.heightDp ?: return false) * from.density
        val xPx = from.left + x * from.density
        var best: Pane? = null
        var bestGap = Double.MAX_VALUE
        var bestX = 0.0
        for (pane in panes) {
            val glass = pane.glass
            if (pane === from || glass == null || pane.density <= 0) continue
            val gap = (pane.top - bottom) / pane.density
            if (gap < -OVERLAP_DP || gap > MAX_GAP_DP || gap >= bestGap) continue
            val local = (xPx - pane.left) / pane.density
            if (local < r * 0.5 || local > glass.widthDp - r * 0.5) continue
            best = pane
            bestGap = gap
            bestX = local
        }
        val target = best?.glass ?: return false
        target.receive(bestX, r, max(0.0, bestGap) / FALL_SPEED + MIN_FALL_SECONDS)
        return true
    }

    companion object {
        /** Дальше этого просвета (dp) под карточкой считается, что воде падать некуда. */
        const val MAX_GAP_DP = 28.0

        /** Допуск на наложение краёв: положение карточек в окне обновляется с запозданием на кадр. */
        private const val OVERLAP_DP = 1.0

        /** Скорость падения воды через просвет (dp/с) и наименьшее время падения. */
        private const val FALL_SPEED = 160.0
        private const val MIN_FALL_SECONDS = 0.05
    }
}
