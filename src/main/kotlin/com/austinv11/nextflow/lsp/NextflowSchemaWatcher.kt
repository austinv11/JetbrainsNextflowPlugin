package com.austinv11.nextflow.lsp

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import java.io.File

@Service(Service.Level.PROJECT)
class NextflowSchemaWatcher(private val project: Project) {
    init {
        project.messageBus.connect(project).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val lspEvents = events.mapNotNull { toSchemaFileEvent(it) }
                if (lspEvents.isEmpty()) return
                notifyServers(lspEvents)
            }
        })
    }

    private fun toSchemaFileEvent(event: VFileEvent): FileEvent? {
        val path = event.path
        if (!isSchemaPath(path)) return null
        val changeType = when (event) {
            is VFileCreateEvent -> FileChangeType.Created
            is VFileDeleteEvent -> FileChangeType.Deleted
            is VFileContentChangeEvent -> FileChangeType.Changed
            else -> FileChangeType.Changed
        }
        return FileEvent(File(path).toURI().toString(), changeType)
    }

    private fun isSchemaPath(path: String): Boolean = File(path).name == "nextflow_schema.json"

    private fun notifyServers(lspEvents: List<FileEvent>) {
        val servers = LspServerManager.getInstance(project)
            .getServersForProvider(NextflowLspServerSupportProvider::class.java)
            .filter { it.state == LspServerState.Running }
        if (servers.isEmpty()) return

        val params = DidChangeWatchedFilesParams(lspEvents)
        servers.forEach { server ->
            server.sendNotification { it.workspaceService.didChangeWatchedFiles(params) }
        }
    }
}
