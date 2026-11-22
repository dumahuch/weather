package io.github.dumahuch.pogodaapp.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenFadeTest {
    private val home = ScreenKey(Layer.Tab, 0)
    private val forecast = ScreenKey(Layer.Tab, 2)
    private val day = ScreenKey(Layer.Day, 2)
    private val places = ScreenKey(Layer.Places)
    private val settings = ScreenKey(Layer.Settings)

    @Test
    fun fadeKeepsTheTimingOfTheOldTransitions() {
        // Углубление: новое окно проявляется, прежнее гаснет за одно и то же время.
        assertEquals(FadeTiming(PUSH_COVER_MS), fadeTiming(TransitionKind.Push, entering = true))
        assertEquals(FadeTiming(PUSH_COVER_MS, easing = LinearEasing), fadeTiming(TransitionKind.Push, entering = false))
        // Возврат: оба с задержкой, прежнее окно проявляется дольше, чем гаснет уходящее.
        assertEquals(FadeTiming(POP_IN_MS, POP_DELAY_MS, LinearOutSlowInEasing), fadeTiming(TransitionKind.Pop, entering = true))
        val out = fadeTiming(TransitionKind.Pop, entering = false)!!
        assertEquals(POP_DELAY_MS, out.delayMillis)
        assertEquals(LinearEasing, out.easing)
        // Без перехода окно не гаснет и не проявляется.
        assertNull(fadeTiming(TransitionKind.None, entering = true))
        assertNull(fadeTiming(TransitionKind.None, entering = false))
    }

    @Test
    fun movesRememberTheLastScreen() {
        val moves = ScreenMoves(home)
        assertEquals(TransitionKind.None, moves.move(forecast))
        assertEquals(TransitionKind.Push, moves.move(day))
        assertEquals(TransitionKind.Pop, moves.move(forecast))
        assertEquals(TransitionKind.Push, moves.move(places))
        assertEquals(TransitionKind.Fade, moves.move(settings))
        assertEquals(TransitionKind.None, moves.move(settings))
    }
}
