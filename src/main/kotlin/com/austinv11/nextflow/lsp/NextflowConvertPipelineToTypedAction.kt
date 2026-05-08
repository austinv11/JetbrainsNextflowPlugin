package com.austinv11.nextflow.lsp

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import org.eclipse.lsp4j.ExecuteCommandParams

class NextflowConvertPipelineToTypedAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val serverManager = LspServerManager.getInstance(project)
        val hasRunningServer = serverManager.getServersForProvider(NextflowLspServerSupportProvider::class.java)
            .any { it.state == LspServerState.Running }
        e.presentation.isEnabledAndVisible = hasRunningServer
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return

        val dialog = NextflowConvertPipelineDialog(project, basePath)
        if (!dialog.showAndGet()) return

        val targetFolder = dialog.getSelectedFolder()
        if (targetFolder.isBlank()) return

        val serverManager = LspServerManager.getInstance(project)
        val server = serverManager.getServersForProvider(NextflowLspServerSupportProvider::class.java)
            .firstOrNull { it.state == LspServerState.Running }

        if (server == null) {
            Messages.showErrorDialog(project, "Nextflow LSP server is not running.", "Error")
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val result = server.sendRequestSync { languageServer ->
                    languageServer.workspaceService.executeCommand(
                        ExecuteCommandParams().apply {
                            this.command = "nextflow.server.convertPipelineToTyped"
                            this.arguments = listOf(targetFolder)
                        }
                    )
                }

                ApplicationManager.getApplication().invokeLater {
                    Messages.showInfoMessage(
                        project,
                        "Converted pipeline to static types. Please review updated code for errors.",
                        "Pipeline Conversion Successful"
                    )
                }
            } catch (ex: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        project,
                        "Failed to convert pipeline to static types: ${ex.message}",
                        "Conversion Error"
                    )
                }
            }
        }
    }
}