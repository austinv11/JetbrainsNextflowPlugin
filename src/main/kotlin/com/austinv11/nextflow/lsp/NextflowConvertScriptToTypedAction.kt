package com.austinv11.nextflow.lsp

import com.austinv11.nextflow.util.NextflowFileUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.Messages
import com.intellij.platform.lsp.api.LspServer
import org.eclipse.lsp4j.ExecuteCommandParams

class NextflowConvertScriptToTypedAction : AnAction() {
    private val command = "nextflow.server.convertScriptToTyped"

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        if (project == null || file == null || !(NextflowFileUtils.isNextflowScript(file) || NextflowFileUtils.isNextflowConfig(file))) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        e.presentation.isEnabledAndVisible = findNextflowCommandServer(project, command) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val server = findNextflowCommandServer(project, command) ?: return

        // Flush unsaved changes so the server reads positions from the same content
        // that IntelliJ's in-memory document holds. workspace/applyEdit positions
        // would be wrong if disk differs from memory when the server builds its AST.
        FileDocumentManager.getInstance().saveAllDocuments()

        // Move all carets to offset 0 before the command. IntelliJ's workspace/applyEdit
        // handler uses the caret as a split boundary when the caret falls inside a
        // replacement range, leaving content after it intact and causing duplication.
        val document = FileDocumentManager.getInstance().getDocument(file)
        if (document != null) {
            EditorFactory.getInstance().getEditors(document, project)
                .forEach { it.caretModel.moveToOffset(0) }
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                check(server.supportsNextflowCommand(command)) { "The connected language server no longer supports script conversion." }
                server.sendRequestSync(LspServer.DEFAULT_REQUEST_TIMEOUT_MS) { languageServer ->
                    languageServer.workspaceService.executeCommand(
                        ExecuteCommandParams().apply {
                            this.command = this@NextflowConvertScriptToTypedAction.command
                            this.arguments = listOf(server.getDocumentIdentifier(file).uri)
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
