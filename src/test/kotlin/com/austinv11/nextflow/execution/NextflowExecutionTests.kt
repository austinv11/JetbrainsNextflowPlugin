package com.austinv11.nextflow.execution

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.execution.configurations.ConfigurationTypeUtil

class NextflowExecutionTests : BasePlatformTestCase() {

    override fun runInDispatchThread(): Boolean {
        return false 
    }

    fun testRunConfigurationType() {
        val type = NextflowRunConfigurationType.getInstance()
        assertNotNull(type)
        assertEquals("Nextflow", type.displayName)
        assertNotNull(type.icon)
        val factories = type.configurationFactories
        assertEquals(1, factories.size)
    }
    
    fun testRunConfigurationOptions() {
        val options = NextflowRunConfigurationOptions()
        assertEquals("", options.scriptPath)
        options.scriptPath = "/path"
        assertEquals("/path", options.scriptPath)
    }

    fun testRunConfigurationEditorInstantiation() {
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait {
            val type = NextflowRunConfigurationType.getInstance()
            val factory = type.configurationFactories[0]
            val config = NextflowRunConfiguration(project, factory, "Test Config")
            val editor = config.configurationEditor
            assertNotNull(editor)
            assertNotNull(editor.component)
        }
    }
}
