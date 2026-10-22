package com.austinv11.nextflow.lsp

import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState

internal fun LspServer.supportsNextflowCommand(command: String): Boolean =
    state == LspServerState.Running && initializeResult?.capabilities?.executeCommandProvider?.commands?.contains(command) == true

internal fun findNextflowCommandServer(project: Project, command: String): LspServer? =
    LspServerManager.getInstance(project)
        .getServersForProvider(NextflowLspServerSupportProvider::class.java)
        .firstOrNull { it.supportsNextflowCommand(command) }
