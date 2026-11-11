package io.github.dumahuch.pogodaapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Подсветка нажатия вместо стандартной волны Material (0.7.14). Замечание Димы 11.11.2026: при прокрутке длинных карточек
 * настроек то одна, то другая строка на миг светлела серой полосой (подсветка нажатия, когда палец ложится на строку в начале
 * протягивания). Здесь подсветка появляется, только если палец держится дольше [HOLD_MILLIS] (протягивание начинается раньше и
 * отменяет нажатие, полос нет), слабая ([STRENGTH]) и плавно гаснет.
 */
internal object SoftIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = SoftIndicationNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === SoftIndication

    override fun hashCode(): Int = -1

    /** Сколько миллисекунд палец должен держаться на элементе, чтобы он подсветился. */
    const val HOLD_MILLIS = 120L

    /** Прозрачность белой подсветки при удержании. */
    const val STRENGTH = 0.06f
}

private class SoftIndicationNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val alpha = Animatable(0f)

    override fun onAttach() {
        val scope = coroutineScope
        scope.launch {
            var hold: Job? = null
            source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        hold?.cancel()
                        hold = scope.launch {
                            delay(SoftIndication.HOLD_MILLIS)
                            alpha.animateTo(SoftIndication.STRENGTH, tween(FADE_IN_MILLIS))
                        }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        hold?.cancel()
                        scope.launch { alpha.animateTo(0f, tween(FADE_OUT_MILLIS)) }
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val value = alpha.value
        if (value > 0f) {
            drawRoundRect(Color.White.copy(alpha = value), cornerRadius = CornerRadius(CORNER_DP * density))
        }
    }

    private companion object {
        const val FADE_IN_MILLIS = 80
        const val FADE_OUT_MILLIS = 220
        const val CORNER_DP = 20f
    }
}
