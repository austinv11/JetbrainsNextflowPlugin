package com.austinv11.nextflow.lsp

import com.austinv11.nextflow.NextflowIcons
import com.austinv11.nextflow.NextflowSettings
import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.LspServerSupportProvider.*
import com.intellij.platform.lsp.api.lsWidget.LspServerWidgetItem

class NextflowLspServerSupportProvider : LspServerSupportProvider {

    companion object {
        private var hasCheckedNextflow = false
    }

    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LspServerStarter
    ) {
        if (file.extension == "nf" || file.name == "nextflow.config") {
            project.getService(NextflowSchemaWatcher::class.java)
            serverStarter.ensureServerStarted(NextflowLspServerDescriptor(project))

            if (!hasCheckedNextflow) {
                hasCheckedNextflow = true
                ApplicationManager.getApplication().executeOnPooledThread {
                    val version = NextflowSettings.getInstance(project).detectInstalledVersion()
                    if (version == null) {
                        val notification = NotificationGroupManager.getInstance()
                            .getNotificationGroup("Nextflow Missing")
                            .createNotification("Nextflow not detected", "Nextflow is unable to be found.", NotificationType.WARNING)
                        notification.addAction(NotificationAction.createSimple("Install Nextflow") {
                            BrowserUtil.browse("https://docs.seqera.io/nextflow/install")
                            notification.expire()
                        })
                        notification.notify(project)
                    }
                }
            }
        }
    }

    override fun createLspServerWidgetItem(lspServer: LspServer, currentFile: VirtualFile?) = LspServerWidgetItem(lspServer, currentFile,
        NextflowIcons.FILE)
}
