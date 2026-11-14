package io.github.dumahuch.pogodaapp.ui.sky

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyBackgroundSnapshotTest {
    @Test
    fun noSnapshotIsDue() {
        assertTrue(SkyBackgroundSnapshot.isDue(null))
    }

    @Test
    fun snapshotFromOpenAppIsKeptWhileFresh() {
        assertFalse(SkyBackgroundSnapshot.isDue(5 * 60_000L))
        assertFalse(SkyBackgroundSnapshot.isDue(19 * 60_000L))
    }

    @Test
    fun oldSnapshotIsReplaced() {
        assertTrue(SkyBackgroundSnapshot.isDue(20 * 60_000L))
        assertTrue(SkyBackgroundSnapshot.isDue(6 * 3_600_000L))
    }
}
