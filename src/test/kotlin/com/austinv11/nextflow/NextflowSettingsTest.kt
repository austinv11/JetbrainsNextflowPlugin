package com.austinv11.nextflow

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NextflowSettingsTest : BasePlatformTestCase() {
    fun testSettingsPanelValidatesLanguageVersionUnlessCustomJarOverridesIt() {
        val settings = NextflowSettings.getInstance(project)
        val original = settings.state.copy()
        try {
            settings.state.languageVersion = "latest"
            val invalidPanel = NextflowSettingsConfigurable(project).createPanel()
            invalidPanel.registerValidators(testRootDisposable)
            invalidPanel.reset()
            assertTrue(invalidPanel.validateAll().any { it.message.contains("release series") })

            settings.state.customLspJarPath = "custom-server.jar"
            val customPanel = NextflowSettingsConfigurable(project).createPanel()
            customPanel.registerValidators(testRootDisposable)
            customPanel.reset()
            assertEmpty(customPanel.validateAll())

            settings.state.customLspJarPath = ""
            settings.state.languageVersion = "26.04.2"
            val validPanel = NextflowSettingsConfigurable(project).createPanel()
            validPanel.registerValidators(testRootDisposable)
            validPanel.reset()
            assertEmpty(validPanel.validateAll())
            validPanel.apply()
            assertEquals("26.04.2", settings.state.languageVersion)
        } finally {
            settings.loadState(original)
        }
    }

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
