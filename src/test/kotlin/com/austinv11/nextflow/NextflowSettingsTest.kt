package com.austinv11.nextflow

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NextflowSettingsTest : BasePlatformTestCase() {
    fun testDefaultSettings() {
        val settings = NextflowSettings.getInstance(project)
        val state = settings.state
        assertEquals("warnings", state.errorReportingMode)
        assertEquals(true, state.completionExtended)
        assertEquals(50, state.completionMaxItems)
        assertEquals(false, state.formattingHarshilAlignment)
        assertEquals(".git,.nf-test,work", state.filesExclude)
        assertEquals(false, state.debugMode)
    }

    fun testUpdateSettings() {
        val settings = NextflowSettings.getInstance(project)
        val state = settings.state
        state.errorReportingMode = "errors"
        state.completionMaxItems = 100

        settings.loadState(state)

        val newState = NextflowSettings.getInstance(project).state
        assertEquals("errors", newState.errorReportingMode)
        assertEquals(100, newState.completionMaxItems)
    }
}
