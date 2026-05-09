package com.austinv11.nextflow.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class NextflowToolWindowFactory : ToolWindowFactory {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        val projectPanel = NextflowProjectPanel(project)
        val projectContent = contentFactory.createContent(projectPanel.getContent(), "Project", false)
        toolWindow.contentManager.addContent(projectContent)

        val resourcesPanel = NextflowResourcesPanel(project)
        val resourcesContent = contentFactory.createContent(resourcesPanel.getContent(), "Resources", false)
        toolWindow.contentManager.addContent(resourcesContent)
    }
}