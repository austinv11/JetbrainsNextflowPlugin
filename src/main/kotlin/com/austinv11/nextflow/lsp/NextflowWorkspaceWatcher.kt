package com.austinv11.nextflow.lsp

import com.austinv11.nextflow.NextflowSettings
import com.austinv11.nextflow.util.NextflowFileUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.util.Alarm

@Service(Service.Level.PROJECT)
class NextflowWorkspaceWatcher(private val project: Project) : Disposable {
    private val log = Logger.getInstance(NextflowWorkspaceWatcher::class.java)
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val changes = NextflowWorkspaceChanges(project)

    init {
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun before(events: List<VFileEvent>) {
                // Deleted files and old names/locations are only available before the change.
                if (changes.requiresRestartBefore(events)) scheduleRestart()
            }

            override fun after(events: List<VFileEvent>) {
                if (changes.requiresRestartAfter(events)) scheduleRestart()
            }
        })
    }

    private fun scheduleRestart() {
        if (project.isDisposed || alarm.isDisposed) return
        val manager = LspServerManager.getInstance(project)
        if (manager.getServersForProvider(NextflowLspServerSupportProvider::class.java).none {
                it.state == LspServerState.Running || it.state == LspServerState.Initializing
            }) return
        alarm.cancelAllRequests()
        alarm.addRequest({ restartIfNeeded() }, 2_000)
    }

    private fun restartIfNeeded() {
        if (project.isDisposed) return
        val manager = LspServerManager.getInstance(project)
        val servers = manager.getServersForProvider(NextflowLspServerSupportProvider::class.java)
        if (servers.any { it.state == LspServerState.Initializing }) {
            scheduleRestart()
        } else if (servers.any { it.state == LspServerState.Running }) {
            // Upstream currently logs watched-file notifications without refreshing its index.
            // The platform restart resends open documents, including their unsaved contents.
            log.info("Restarting Nextflow language server after workspace file changes")
            manager.stopAndRestartIfNeeded(NextflowLspServerSupportProvider::class.java)
        }
    }

    override fun dispose() = Unit
}

internal class NextflowWorkspaceChanges(
    private val project: Project,
    private val isOpen: (VirtualFile) -> Boolean = { FileEditorManager.getInstance(project).isFileOpen(it) }
) {
    fun requiresRestartBefore(events: List<VFileEvent>): Boolean = events.any { event ->
        when (event) {
            is VFileDeleteEvent, is VFileMoveEvent -> containsRelevantFile(event.file)
            is VFilePropertyChangeEvent -> event.propertyName == VirtualFile.PROP_NAME && containsRelevantFile(event.file)
            else -> false
        }
    }

    fun requiresRestartAfter(events: List<VFileEvent>): Boolean = events.any { event ->
        when (event) {
            is VFileCreateEvent, is VFileMoveEvent -> containsRelevantFile(event.file)
            is VFileCopyEvent -> containsRelevantFile(event.findCreatedFile())
            is VFilePropertyChangeEvent -> event.propertyName == VirtualFile.PROP_NAME && containsRelevantFile(event.file)
            is VFileContentChangeEvent -> containsRelevantFile(event.file, skipOpenDocuments = true)
            else -> false
        }
    }

    private fun containsRelevantFile(file: VirtualFile?, skipOpenDocuments: Boolean = false): Boolean {
        if (file == null || !file.isValid) return false
        val index = ProjectFileIndex.getInstance(project)
        val excludes = NextflowSettings.getInstance(project).state.filesExclude.split(',')
            .map { it.trim().replace('\\', '/').trimEnd('/') }.filter { it.isNotEmpty() }

        fun included(candidate: VirtualFile): Boolean {
            if (!candidate.isInLocalFileSystem || !index.isInContent(candidate)) return false
            val root = index.getContentRootForFile(candidate) ?: return false
            var ancestor: VirtualFile? = candidate
            while (ancestor != null) {
                val path = ancestor.path
                if (excludes.any { path == it || path.endsWith("/$it") }) return false
                if (ancestor == root) break
                ancestor = ancestor.parent
            }
            return true
        }

        fun visit(candidate: VirtualFile): Boolean {
            if (!included(candidate)) return false
            if (candidate.isDirectory) return candidate.children.any { visit(it) }
            val script = NextflowFileUtils.isNextflowScript(candidate)
            val config = NextflowFileUtils.isNextflowConfig(project, candidate)
            val schema = candidate.name == "nextflow_schema.json"
            if (!script && !config && !schema) return false
            val synchronizedDocument = NextflowFileUtils.isNextflowLspFile(project, candidate)
            return !(skipOpenDocuments && synchronizedDocument && isOpen(candidate))
        }

        return visit(file)
    }
}
