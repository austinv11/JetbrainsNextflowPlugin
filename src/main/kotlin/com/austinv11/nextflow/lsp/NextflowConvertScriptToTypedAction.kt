package com.austinv11.nextflow.lsp

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import org.eclipse.lsp4j.ExecuteCommandParams

class NextflowConvertScriptToTypedAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        if (project == null || file == null || !(file.extension == "nf" || file.name == "nextflow.config")) {
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
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

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
                            this.command = "nextflow.server.convertScriptToTyped"
                            this.arguments = listOf(file.url)
                        }
                    )
                }

                ApplicationManager.getApplication().invokeLater {
                    Messages.showInfoMessage(
                        project,
                        "Converted script to static types and updated call sites. Please review updated code for errors.",
                        "Script Conversion Successful"
                    )
                }
            } catch (ex: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        project,
                        "Failed to convert script to static types: ${ex.message}",
                        "Conversion Error"
                    )
                }
            }
        }
    }
}