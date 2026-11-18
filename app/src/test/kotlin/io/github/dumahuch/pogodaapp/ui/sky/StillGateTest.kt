package io.github.dumahuch.pogodaapp.ui.sky

import io.github.dumahuch.pogodaapp.ui.sky.StillGate.Step
import org.junit.Assert.assertEquals
import org.junit.Test

class StillGateTest {

    @Test
    fun recordsOnSecondFrameWithSameKeyThenReuses() {
        val gate = StillGate()
        assertEquals(listOf(Step.Direct, Step.Record, Step.Reuse, Step.Reuse), listOf("a", "a", "a", "a").map(gate::step))
    }

    @Test
    fun keyChangingEveryFrameNeverRecords() {
        val gate = StillGate()
        assertEquals(List(4) { Step.Direct }, listOf(1.0, 1.5, 2.0, 2.5).map(gate::step))
    }

    @Test
    fun afterScrollRecordsAgainAndOldPictureIsNotReused() {
        val gate = StillGate()
        listOf("a", "a", "a").forEach(gate::step)
        assertEquals(listOf(Step.Direct, Step.Record, Step.Reuse), listOf("b", "b", "b").map(gate::step))
    }

    @Test
    fun equalKeysMatchByContent() {
        val gate = StillGate()
        gate.step(listOf(1, 2.0))
        assertEquals(Step.Record, gate.step(listOf(1, 2.0)))
    }

    @Test
    fun resetForgetsRecordedPicture() {
        val gate = StillGate()
        listOf("a", "a").forEach(gate::step)
        gate.reset()
        assertEquals(Step.Direct, gate.step("a"))
    }
}
