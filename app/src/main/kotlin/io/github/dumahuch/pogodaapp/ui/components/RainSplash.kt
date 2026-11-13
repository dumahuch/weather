package io.github.dumahuch.pogodaapp.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import io.github.dumahuch.pogodaapp.sky.EdgeSplash

/** Цвет брызг: светлая вода с лёгким оттенком неба. */
private val SPLASH_COLOR = Color(0xFFE4EEFA)
private const val SPLASH_ALPHA = 0.8f

private var nextSeed = 1L

/**
 * Брызги дождя над верхней кромкой карточки или кнопки. Рисуются выше формы, поэтому ставятся до обрезки по форме (как
 * сугроб). Без дождя и при выключенных анимациях ничего не делает: неподвижные брызги были бы просто крапинками.
 */
internal fun Modifier.rainSplash(rain: RainOnGlass?): Modifier =
    if (rain == null || !rain.animate) this else this then RainSplashElement(rain)

private class RainSplashElement(val rain: RainOnGlass) : ModifierNodeElement<RainSplashNode>() {
    override fun create() = RainSplashNode(rain)

    override fun update(node: RainSplashNode) = node.update(rain)

    override fun equals(other: Any?): Boolean = other is RainSplashElement && other.rain === rain

    override fun hashCode(): Int = System.identityHashCode(rain)

    override fun InspectorInfo.inspectableProperties() {
        name = "rainSplash"
    }
}

private class RainSplashNode(private var rain: RainOnGlass) : Modifier.Node(), DrawModifierNode {

    private val seed = nextSeed++
    private var splash: EdgeSplash? = null
    private var lastTime = Double.NaN

    fun update(rain: RainOnGlass) {
        this.rain = rain
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        val px = density
        val t = rain.clock.value
        val width = size.width / px
        if (width >= 1f) {
            val current = splash?.takeIf { kotlin.math.abs(it.widthDp - width) < 1 } ?: EdgeSplash(width.toDouble(), seed).also { splash = it }
            if (!lastTime.isNaN() && t > lastTime) current.step(t - lastTime, rain.strength)
            lastTime = t
            for (item in current.splashes) {
                val alpha = SPLASH_ALPHA * item.alpha().toFloat()
                for (i in item.vx.indices) {
                    val x = (item.x + item.dx(i)).toFloat() * px
                    val y = -item.height(i).toFloat() * px
                    if (y <= 0f) drawCircle(SPLASH_COLOR.copy(alpha = alpha), item.radius[i].toFloat() * px, Offset(x, y))
                }
            }
        }
        drawContent()
    }
}
