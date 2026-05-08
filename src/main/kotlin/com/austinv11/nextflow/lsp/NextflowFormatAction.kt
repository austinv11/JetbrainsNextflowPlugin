package com.austinv11.nextflow.lsp

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.Messages
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import kotlin.math.min

class NextflowFormatAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (project == null || file == null || !isNextflowFile(file)) {
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
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return

        FileDocumentManager.getInstance().saveAllDocuments()

        val server = LspServerManager.getInstance(project)
            .getServersForProvider(NextflowLspServerSupportProvider::class.java)
            .firstOrNull { it.state == LspServerState.Running }

        if (server == null) {
            Messages.showErrorDialog(project, "Nextflow LSP server is not running.", "Format Error")
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val edits = server.sendRequestSync { languageServer ->
                    languageServer.textDocumentService.formatting(
                        DocumentFormattingParams().apply {
                            textDocument = TextDocumentIdentifier(file.url)
                            options = FormattingOptions(4, true)
                        }
                    )
                } ?: emptyList()

                ApplicationManager.getApplication().invokeLater {
                    if (edits.isEmpty()) {
                        Messages.showInfoMessage(project, "No formatting changes returned by the server.", "Format Nextflow")
                        return@invokeLater
                    }
                    applyEdits(project, document, edits)
                }
            } catch (ex: Exception) {
                val errorMessage = if (ex is ResponseErrorException) {
                    val msg = ex.responseError?.message ?: ex.message
                    val data = ex.responseError?.data?.toString()
                    if (data != null && data.isNotBlank()) {
                        "$msg\nDetails: $data"
                    } else {
                        msg
                    }
                } else {
                    ex.message
                }

                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        project,
                        "Failed to format with Nextflow LSP: $errorMessage",
                        "Format Error"
                    )
                }
            }
        }
    }

    private fun applyEdits(project: com.intellij.openapi.project.Project, document: Document, edits: List<TextEdit>) {
        val normalized = edits.mapNotNull { edit ->
            val start = toOffset(document, edit.range.start) ?: return@mapNotNull null
            val end = toOffset(document, edit.range.end) ?: return@mapNotNull null
            if (end < start) return@mapNotNull null
            Edit(start, end, edit.newText)
        }.sortedByDescending { it.start }

        if (normalized.isEmpty()) return

        WriteCommandAction.runWriteCommandAction(project) {
            normalized.forEach { edit ->
                document.replaceString(edit.start, edit.end, edit.newText)
            }
        }
    }

    private fun toOffset(document: Document, position: Position): Int? {
        val line = position.line
        if (line < 0 || line >= document.lineCount) return null
        val lineStart = document.getLineStartOffset(line)
        val lineEnd = document.getLineEndOffset(line)
        val offset = lineStart + position.character
        return min(offset, lineEnd)
    }

    private fun isNextflowFile(file: com.intellij.openapi.vfs.VirtualFile): Boolean {
        return file.extension == "nf" || file.name == "nextflow.config"
    }

    private data class Edit(val start: Int, val end: Int, val newText: String)
}
