package io.github.dumahuch.pogodaapp.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenKeyTest {
    private val home = ScreenKey(Layer.Tab, 0)
    private val details = ScreenKey(Layer.Tab, 1)
    private val forecast = ScreenKey(Layer.Tab, 2)
    private val map = ScreenKey(Layer.Tab, 3, isMap = true)
    private val day = ScreenKey(Layer.Day, 2)
    private val places = ScreenKey(Layer.Places)
    private val settings = ScreenKey(Layer.Settings)

    @Test
    fun tabsSwitchInstantly() {
        assertEquals(TransitionKind.None, transitionKind(home, forecast))
        assertEquals(TransitionKind.None, transitionKind(forecast, details))
        assertEquals(TransitionKind.None, transitionKind(forecast, map))
        assertEquals(TransitionKind.None, transitionKind(map, home))
    }

    @Test
    fun oldTabPictureWaitsForShortFrame() {
        // Первый кадр сразу после смены ещё старый, даже если короткий.
        assertEquals(false, newTabSettled(gapMs = 1, framesSeen = 1, waitedMs = 1))
        // Сборка новой вкладки: длинный кадр, ждём дальше.
        assertEquals(false, newTabSettled(gapMs = 187, framesSeen = 2, waitedMs = 188))
        // Кадр снова короткий: гасим.
        assertEquals(true, newTabSettled(gapMs = 33, framesSeen = 4, waitedMs = 355))
        // Предел ожидания, даже если кадры длинные.
        assertEquals(true, newTabSettled(gapMs = 120, framesSeen = 3, waitedMs = SETTLE_MAX_MS))
    }

    @Test
    fun skyIsOnlyTheTimeAfterExitEnds() {
        // Новая вкладка готова до конца ухода: неба нет.
        assertEquals(0L, skyMillis(exitEndNanos = 150_000_000L, arriveNanos = 120_000_000L))
        assertEquals(0L, skyMillis(exitEndNanos = 150_000_000L, arriveNanos = 150_000_000L))
        // Не успела: небо видно, пока она не готова.
        assertEquals(40L, skyMillis(exitEndNanos = 150_000_000L, arriveNanos = 190_500_000L))
    }

    @Test
    fun frameGapsAreMillisecondsBetweenNeighbours() {
        assertEquals(listOf(89L, 8L), frameGaps(listOf(0L, 89_400_000L, 97_900_000L)))
        assertEquals(emptyList<Long>(), frameGaps(listOf(5L)))
    }

    @Test
    fun deeperWindowsPushAndPop() {
        assertEquals(TransitionKind.Push, transitionKind(forecast, day))
        assertEquals(TransitionKind.Pop, transitionKind(day, forecast))
        assertEquals(TransitionKind.Push, transitionKind(home, places))
        assertEquals(TransitionKind.Pop, transitionKind(settings, home))
    }

    @Test
    fun sameWindowAndOddPairs() {
        assertEquals(TransitionKind.None, transitionKind(home, home))
        assertEquals(TransitionKind.Fade, transitionKind(places, settings))
    }
}
