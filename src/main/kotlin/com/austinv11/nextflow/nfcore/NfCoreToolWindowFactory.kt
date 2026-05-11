package com.austinv11.nextflow.nfcore

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class NfCoreToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val nfCorePanel = NfCorePanel(project)
        val content = contentFactory.createContent(nfCorePanel.getContent(), "nf-core", false)
        toolWindow.contentManager.addContent(content)
    }
}
