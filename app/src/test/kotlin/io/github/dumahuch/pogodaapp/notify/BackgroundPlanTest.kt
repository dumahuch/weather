package io.github.dumahuch.pogodaapp.notify

import io.github.dumahuch.pogodaapp.settings.NotificationPrefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundPlanTest {
    private val on = NotificationPrefs(inShade = true, alerts = true, summary = true)
    private val off = NotificationPrefs()

    @Test
    fun allowedAndSwitchedOnNeedsTheJob() {
        assertTrue(BackgroundPlan.periodicJobNeeded(on, allowed = true, hasWidgets = false))
    }

    @Test
    fun blockedWithoutWidgetsNeedsNoJob() {
        assertFalse(BackgroundPlan.periodicJobNeeded(on, allowed = false, hasWidgets = false))
    }

    @Test
    fun blockedButWithWidgetKeepsTheJobForTheWidget() {
        assertTrue(BackgroundPlan.periodicJobNeeded(on, allowed = false, hasWidgets = true))
    }

    @Test
    fun allSwitchesOffWithoutWidgetsNeedsNoJob() {
        assertFalse(BackgroundPlan.periodicJobNeeded(off, allowed = true, hasWidgets = false))
        assertTrue(BackgroundPlan.periodicJobNeeded(off, allowed = true, hasWidgets = true))
    }

    @Test
    fun summaryJobNeedsSwitchAndPermission() {
        assertTrue(BackgroundPlan.summaryJobNeeded(on, allowed = true))
        assertFalse(BackgroundPlan.summaryJobNeeded(on, allowed = false))
        assertFalse(BackgroundPlan.summaryJobNeeded(off, allowed = true))
    }
}
