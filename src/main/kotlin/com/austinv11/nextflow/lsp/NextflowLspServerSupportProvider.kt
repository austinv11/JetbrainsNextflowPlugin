package com.austinv11.nextflow.lsp

import com.austinv11.nextflow.NextflowIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.LspServerSupportProvider.*
import com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem

class NextflowLspServerSupportProvider : LspServerSupportProvider {

    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LspServerStarter
    ) {
        if (file.extension == "nf" || file.name == "nextflow.config") {
            serverStarter.ensureServerStarted(NextflowLspServerDescriptor(project))
        }
    }

    override fun createLspServerWidgetItem(lspServer: LspServer, currentFile: VirtualFile?) = LspServerWidgetItem(lspServer, currentFile,
        NextflowIcons.FILE)
}