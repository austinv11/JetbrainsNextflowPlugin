package com.austinv11.nextflow

import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.customization.LspCommandsSupport
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.UIUtil
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.ExecuteCommandParams
import java.awt.Dimension
import java.awt.Font
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.swing.JComponent
import javax.swing.JTextArea

class NextflowLspCommandsSupport : LspCommandsSupport() {

    override fun executeCommand(server: LspServer, file: VirtualFile, command: Command) {
        if (command.command == "nextflow.previewDag") {
            previewDag(server, command)
        } else {
            super.executeCommand(server, file, command)
        }
    }

    private fun previewDag(server: LspServer, command: Command) {
        val project = server.project
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val result = server.sendRequestSync { languageServer ->
                    languageServer.workspaceService.executeCommand(
                        ExecuteCommandParams().apply {
                            this.command = "nextflow.server.previewDag"
                            this.arguments = command.arguments
                        }
                    )
                }
                ApplicationManager.getApplication().invokeLater {
                    val diagram = (result as LinkedTreeMap<*, *>)["result"] as? String
                    when {
                        diagram != null -> DagPreviewDialog(project, diagram).show()
                        else -> Messages.showErrorDialog(project, "Unexpected response from server", "DAG Preview Error")
                    }
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(project, e.message ?: "Unknown error", "DAG Preview Error")
                }
            }
        }
    }
}

private class DagPreviewDialog(project: com.intellij.openapi.project.Project, private val mermaidText: String) :
    DialogWrapper(project, false) {

    private val logger = Logger.getInstance(DagPreviewDialog::class.java)
    private var browser: JBCefBrowser? = null

    init {
        title = "DAG Preview (Mermaid)"
        isModal = false
        init()
    }

    override fun createCenterPanel(): JComponent {
        if (JBCefApp.isSupported()) {
            val browser = JBCefBrowser().also { this.browser = it }
            val mermaidUrl = resolveMermaidUrl()
            browser.jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
                override fun onConsoleMessage(
                    cefBrowser: CefBrowser?,
                    level: CefSettings.LogSeverity?,
                    message: String?,
                    source: String?,
                    line: Int
                ): Boolean {
                    logger.info("DagPreviewDialog console[$level] $source:$line $message")
                    return false
                }
            }, browser.cefBrowser)
            browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    if (frame?.isMain != true) return
                    logger.info("DagPreviewDialog onLoadEnd status=$httpStatusCode url=${cefBrowser?.url}")
                }
            }, browser.cefBrowser)

            val html = buildMermaidHtml(mermaidText, mermaidUrl)
            try {
                val tempFile = Files.createTempFile("nextflow-dag-", ".html").toFile().apply {
                    writeText(html)
                    deleteOnExit()
                }
                browser.loadURL(tempFile.toURI().toString())
            } catch (e: Exception) {
                browser.loadHTML(html, "https://cdn.jsdelivr.net/")
            }

            return browser.component.apply {
                preferredSize = Dimension(900, 600)
            }
        }

        val textArea = JTextArea(mermaidText).apply {
            isEditable = false
            lineWrap = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        }
        return JBScrollPane(textArea).apply {
            preferredSize = Dimension(700, 450)
        }
    }

    private fun resolveMermaidUrl(): String {
        val overrideUrl = System.getProperty("nextflow.mermaid.url")
        if (!overrideUrl.isNullOrBlank()) {
            return overrideUrl
        }

        val resourcePath = "/mermaid/mermaid.min.js"
        val resourceStream = javaClass.getResourceAsStream(resourcePath)
            ?: return "about:blank"

        resourceStream.use { input ->
            val tempFile = Files.createTempFile("mermaid-", ".min.js").toFile().apply {
                deleteOnExit()
            }
            Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return tempFile.toURI().toString()
        }
    }

    override fun dispose() {
        browser?.dispose()
        super.dispose()
    }

    override fun createActions() = arrayOf(okAction)

    private fun buildMermaidHtml(mermaidText: String, mermaidUrl: String): String {
        val mermaidJson = Gson().toJson(mermaidText)
        val mermaidUrlJson = Gson().toJson(mermaidUrl)
        val themeJson = Gson().toJson(if (JBColor.isBright()) "light" else "dark")
        val templateStream = javaClass.getResourceAsStream("/mermaid/preview.html")
            ?: error("Missing /mermaid/preview.html resource")
        val template = templateStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return template
            .replace("\"__MERMAID_URL__\"", mermaidUrlJson)
            .replace("\"__MERMAID_TEXT__\"", mermaidJson)
            .replace("\"__IDE_THEME__\"", themeJson)
    }
}