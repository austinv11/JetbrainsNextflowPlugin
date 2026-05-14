package com.austinv11.nextflow.nfcore

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NfCoreToolWindowFactoryTest : BasePlatformTestCase() {

    override fun runInDispatchThread(): Boolean {
        return false 
    }

    fun testShouldBeAvailable() {
        val factory = NfCoreToolWindowFactory()
        assertTrue(factory.shouldBeAvailable(project))
    }

    fun testCreateToolWindowContent() {
        val factory = NfCoreToolWindowFactory()
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait {
            val toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).registerToolWindow(
                com.intellij.openapi.wm.RegisterToolWindowTask("nf-core")
            )
            factory.createToolWindowContent(project, toolWindow)
            val contentManager = toolWindow.contentManager
            // Just verifying it ran, checking counts can be flaky if another test registers windows
            assertTrue(contentManager.contentCount >= 2)
        }
    }
}
