package com.austinv11.nextflow.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import org.eclipse.lsp4j.Command
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.util.concurrent.CancellationException
import java.util.concurrent.Future
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

internal fun showNextflowConfigPreview(server: LspServer, command: Command) {
    val project = server.project
    ApplicationManager.getApplication().invokeLater {
        if (project.isDisposed) return@invokeLater
        try {
            val request = NextflowConfigPreviewRequest.fromCommand(command)
            val dialog = NextflowConfigPreviewDialog(project, request) { loadNextflowConfigPreview(server, it) }
            dialog.show()
            dialog.refreshPreview()
        } catch (e: Exception) {
            if (e is ProcessCanceledException) throw e
            Messages.showErrorDialog(project, e.message ?: "Unable to open the configuration preview.", "Config Preview")
        }
    }
}

internal fun loadNextflowConfigPreview(server: LspServer, request: NextflowConfigPreviewRequest): NextflowConfigPreview {
    // Configuration changes can restart the server while the preview is open.
    val currentServer = if (server.state == LspServerState.Running) server else
        LspServerManager.getInstance(server.project).getServersForProvider(NextflowLspServerSupportProvider::class.java)
            .firstOrNull { it.state == LspServerState.Running }
            ?: error("The language server is restarting. Try Refresh again once it is ready.")
    val result = currentServer.sendRequestSync { languageServer ->
        languageServer.workspaceService.executeCommand(request.toParams())
    }
    return NextflowConfigPreview.fromResponse(result)
}

internal class NextflowConfigPreviewDialog(
    private val previewProject: Project,
    private val request: NextflowConfigPreviewRequest,
    private val loadPreview: (NextflowConfigPreviewRequest) -> NextflowConfigPreview
) : DialogWrapper(previewProject, false) {
    internal val profilesSelector = NextflowProfileSelector(request.profiles) { scheduleProfileRefresh() }
    internal val previewText = JBTextArea().apply {
        isEditable = false
        val scheme = EditorColorsManager.getInstance().globalScheme
        font = Font(scheme.editorFontName, Font.PLAIN, scheme.editorFontSize)
    }
    internal val refreshButton = JButton("Refresh").apply { addActionListener { refreshPreview() } }
    private var loading: Future<*>? = null
    private var requestInProgress = false
    private var refreshRequested = false
    private val profileRefreshTimer = Timer(200) { refreshPreview() }.apply { isRepeats = false }

    init {
        title = "Config Preview: ${request.process}"
        isModal = false
        setCancelButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 8)).apply {
        val controls = JPanel(BorderLayout(8, 0)).apply {
            add(JBLabel("Profiles:").apply { labelFor = profilesSelector }, BorderLayout.WEST)
            add(profilesSelector, BorderLayout.CENTER)
            add(refreshButton, BorderLayout.EAST)
        }
        add(controls, BorderLayout.NORTH)
        add(JBScrollPane(previewText), BorderLayout.CENTER)
        preferredSize = Dimension(850, 500)
    }

    internal fun refreshPreview() {
        if (previewProject.isDisposed || isDisposed) return
        profileRefreshTimer.stop()
        if (requestInProgress) {
            refreshRequested = true
            return
        }
        refreshRequested = false
        requestInProgress = true
        val nextRequest = request.copy(profiles = profilesSelector.selectedProfiles)
        refreshButton.isEnabled = false
        previewText.text = "Loading configuration preview…"
        loading = ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val preview = loadPreview(nextRequest)
                updatePreview {
                    profilesSelector.setAvailableProfiles(preview.profiles)
                    previewText.text = preview.renderText()
                }
            } catch (e: Exception) {
                val cancelled = e is ProcessCanceledException || e is CancellationException || e is InterruptedException
                updatePreview {
                    previewText.text = if (cancelled) "Preview request cancelled. Click Refresh to try again."
                    else "Unable to load configuration preview.\n\n${e.message ?: "Unknown error"}"
                }
                if (e is ProcessCanceledException) throw e
                if (e is InterruptedException) Thread.currentThread().interrupt()
            }
        }
    }

    private fun scheduleProfileRefresh() {
        if (previewProject.isDisposed || isDisposed) return
        refreshRequested = true
        refreshButton.isEnabled = false
        profileRefreshTimer.restart()
    }

    private fun updatePreview(update: () -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            if (previewProject.isDisposed || isDisposed) return@invokeLater
            requestInProgress = false
            // Keep the picker usable while loading and never publish a response for an older selection.
            if (refreshRequested) {
                if (!profileRefreshTimer.isRunning) refreshPreview()
                return@invokeLater
            }
            update()
            previewText.caretPosition = 0
            refreshButton.isEnabled = true
            profilesSelector.isEnabled = profilesSelector.hasProfiles
        }
    }

    override fun createActions() = arrayOf(cancelAction)

    override fun dispose() {
        profileRefreshTimer.stop()
        loading?.cancel(true)
        super.dispose()
    }
}
