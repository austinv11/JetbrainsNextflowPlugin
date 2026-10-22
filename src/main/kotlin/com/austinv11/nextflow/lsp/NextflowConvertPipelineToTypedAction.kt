package com.austinv11.nextflow.lsp

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import org.eclipse.lsp4j.ExecuteCommandParams

class NextflowConvertPipelineToTypedAction : AnAction() {
    private val command = "nextflow.server.convertPipelineToTyped"

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isEnabledAndVisible = findNextflowCommandServer(project, command) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return
        if (findNextflowCommandServer(project, command) == null) return

        val dialog = NextflowConvertPipelineDialog(project, basePath)
        if (!dialog.showAndGet()) return

        val targetFolder = dialog.getSelectedFolder()
        if (targetFolder.isBlank()) return

        val server = findNextflowCommandServer(project, command) ?: return

        // Flush unsaved changes so the server reads positions from the same content
        // that IntelliJ's in-memory documents hold. workspace/applyEdit positions
        // would be wrong if disk differs from memory when the server builds its AST.
        FileDocumentManager.getInstance().saveAllDocuments()

        // Move all carets to offset 0 in every open file under the target folder.
        // IntelliJ's workspace/applyEdit handler uses the caret as a split boundary
        // when the caret falls inside a replacement range, causing duplication.
        val fdm = FileDocumentManager.getInstance()
        for (openFile in FileEditorManager.getInstance(project).openFiles) {
            if (openFile.path.startsWith(targetFolder)) {
                val doc = fdm.getDocument(openFile) ?: continue
                EditorFactory.getInstance().getEditors(doc, project)
                    .forEach { it.caretModel.moveToOffset(0) }
            }
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                check(server.supportsNextflowCommand(command)) { "The connected language server no longer supports pipeline conversion." }
                server.sendRequestSync { languageServer ->
                    languageServer.workspaceService.executeCommand(
                        ExecuteCommandParams().apply {
                            this.command = this@NextflowConvertPipelineToTypedAction.command
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
