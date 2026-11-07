package io.github.dumahuch.pogodaapp.ui.sky

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import io.github.dumahuch.pogodaapp.sky.ScrimShape
import kotlin.math.abs

/**
 * Границы блоков «Главной», под которыми небо рисует подложки (step-40): блок цифр и строки под температурой.
 * «Главная» сообщает их через [scrimRegion] (пиксели окна), хозяин неба читает при рисовании: изменение перерисовывает
 * только небо, а не интерфейс. Пока границ нет (первый кадр), небо рисует прежнюю форму по координатам макета.
 * Последние присланные границы остаются, когда «Главная» уходит с экрана: подложка на других вкладках не прыгает.
 */
@Stable
internal class ScrimRegions {
    private var heroTop by mutableFloatStateOf(UNKNOWN)
    private var heroBottom by mutableFloatStateOf(UNKNOWN)
    private var infoTop by mutableFloatStateOf(UNKNOWN)
    private var infoBottom by mutableFloatStateOf(UNKNOWN)

    enum class Kind { Hero, Info }

    fun report(kind: Kind, top: Float, bottom: Float) {
        when (kind) {
            Kind.Hero -> {
                if (differs(heroTop, top)) heroTop = top
                if (differs(heroBottom, bottom)) heroBottom = bottom
            }
            Kind.Info -> {
                if (differs(infoTop, top)) infoTop = top
                if (differs(infoBottom, bottom)) infoBottom = bottom
            }
        }
    }

    /**
     * Подложки по границам блоков в пикселях хозяина неба ([hostTop] смещение его верха в окне, [density] пикселей на
     * dp для ширины краёв) или `null`, пока какой-то из блоков не показан.
     */
    fun bands(hostTop: Float, density: Float): ScrimShape.Bands? {
        if (heroTop.isNaN() || heroBottom.isNaN() || infoTop.isNaN() || infoBottom.isNaN()) return null
        return ScrimShape.around(
            heroTop = (heroTop - hostTop).toDouble(),
            heroBottom = (heroBottom - hostTop).toDouble(),
            infoTop = (infoTop - hostTop).toDouble(),
            infoBottom = (infoBottom - hostTop).toDouble(),
            heroFeather = (HERO_FEATHER_DP * density).toDouble(),
            infoFeather = (INFO_FEATHER_DP * density).toDouble(),
        )
    }

    private fun differs(old: Float, new: Float) = old.isNaN() || abs(old - new) >= MIN_CHANGE_PX

    private companion object {
        const val UNKNOWN = Float.NaN

        /** Края подложки мягкие: у цифр шире (они крупные, их края размыты небом), у строк уже. */
        const val HERO_FEATHER_DP = 56f
        const val INFO_FEATHER_DP = 40f

        /** Сдвиг меньше пикселя не сообщается: небо не перерисовывается из-за округления. */
        const val MIN_CHANGE_PX = 1f
    }
}

internal val LocalScrimRegions = staticCompositionLocalOf { ScrimRegions() }

/** Сообщает небу границы этого блока (его положение в окне) для подложки вида [kind]. */
internal fun Modifier.scrimRegion(regions: ScrimRegions, kind: ScrimRegions.Kind): Modifier =
    onGloballyPositioned { coordinates ->
        val top = coordinates.positionInRoot().y
        regions.report(kind, top, top + coordinates.size.height)
    }
