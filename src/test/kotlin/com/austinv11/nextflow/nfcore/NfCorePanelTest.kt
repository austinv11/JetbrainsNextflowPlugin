package com.austinv11.nextflow.nfcore

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NfCorePanelTest : BasePlatformTestCase() {

    override fun runInDispatchThread(): Boolean {
        return false 
    }

    fun testPanelInstantiationAndContent() {
        val panel = NfCorePanel(project)
        val content = panel.getContent()
        assertNotNull(content)
    }
}
