package com.austinv11.nextflow.nfcore

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.content.ContentFactory

class NfCoreToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        // Commands Tab
        val nfCorePanel = NfCorePanel(project)
        val commandsContent = contentFactory.createContent(nfCorePanel.getContent(), "Commands", false)
        toolWindow.contentManager.addContent(commandsContent)

        // Browser Tab
        val browserPanel = NfCoreBrowserPanel(project)
        val browserContent = contentFactory.createContent(browserPanel.getContent(), "Browser", false)
        browserContent.setDisposer(browserPanel)
        toolWindow.contentManager.addContent(browserContent)
    }
}
