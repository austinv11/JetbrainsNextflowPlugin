package com.austinv11.nextflow.lsp

import com.austinv11.nextflow.NextflowSettings
import com.google.gson.JsonObject
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.eclipse.lsp4j.ConfigurationItem

class NextflowLspServerDescriptorTest : BasePlatformTestCase() {

    fun testWorkspaceConfigurationGeneratesCorrectly() {
        // Setup initial configuration
        val settings = NextflowSettings.getInstance(project)
        val state = settings.state
        state.errorReportingMode = "paranoid"
        state.languageVersion = "23.04.0"
        state.completionExtended = false
        state.completionMaxItems = 100
        state.formattingHarshilAlignment = true
        state.formattingSortDeclarations = false
        state.formattingMaheshForm = true
        state.filesExclude = ".git,work"
        state.debugMode = true

        settings.loadState(state)

        // Invoke descriptor
        val descriptor = NextflowLspServerDescriptor(project)

        val configItem = ConfigurationItem()
        configItem.section = "nextflow"

        val configuration = descriptor.getWorkspaceConfiguration(configItem) as JsonObject

        assertNotNull(configuration)
        assertEquals("paranoid", configuration.get("errorReportingMode").asString)
        assertEquals("23.04.0", configuration.get("languageVersion").asString)
        assertEquals(true, configuration.get("debug").asBoolean)

        val filesObj = configuration.getAsJsonObject("files")
        val excludeArray = filesObj.getAsJsonArray("exclude")
        assertEquals(2, excludeArray.size())
        assertEquals(".git", excludeArray.get(0).asString)
        assertEquals("work", excludeArray.get(1).asString)

        val completionObj = configuration.getAsJsonObject("completion")
        assertEquals(false, completionObj.get("extended").asBoolean)
        assertEquals(100, completionObj.get("maxItems").asInt)

        val formattingObj = configuration.getAsJsonObject("formatting")
        assertEquals(true, formattingObj.get("harshilAlignment").asBoolean)
        assertEquals(false, formattingObj.get("sortDeclarations").asBoolean)
        assertEquals(true, formattingObj.get("maheshForm").asBoolean)
    }
}
