package com.austinv11.nextflow.nfcore

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NfCoreBrowserPanelTest : BasePlatformTestCase() {

    override fun runInDispatchThread(): Boolean {
        return false
    }

    fun testPanelInstantiationAndContent() {
        val panel = NfCoreBrowserPanel(project)
        val content = panel.getContent()
        assertNotNull(content)
        panel.dispose()
    }
}
