package com.austinv11.nextflow

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerListener
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.WindowClientCapabilities

class NextflowLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "Nextflow LSP") {

    override fun isSupportedFile(file: VirtualFile): Boolean = file.extension == "nf" || file.name == "nextflow.config"

    override fun getLanguageId(file: VirtualFile): String = when {
        file.extension == "nf" -> "nextflow"
        file.name == "nextflow.config" -> "nextflow-config"
        else -> super.getLanguageId(file)
    }

    override fun createCommandLine(): GeneralCommandLine {
        val serverJar = NextflowLspServerDownloader.getOrDownloadLspServer()
            ?: throw RuntimeException("Failed to locate or download Nextflow LSP server JAR")

        // Construct the Java command to run the server jar
        val javaExe = System.getProperty("java.home") + "/bin/java"
        return GeneralCommandLine(javaExe, "-jar", serverJar.absolutePath)
    }

    override fun getWorkspaceConfiguration(item: ConfigurationItem): Any? {
        if (item.section == "nextflow") {
            return buildNextflowConfig()
        }
        return null
    }

    override val lspServerListener = object : LspServerListener {
        override fun serverInitialized(params: InitializeResult) {
            val server = LspServerManager.getInstance(project)
                .getServersForProvider(NextflowLspServerSupportProvider::class.java)
                .firstOrNull { it.state == LspServerState.Running } ?: return
            server.sendNotification { languageServer ->
                languageServer.workspaceService.didChangeConfiguration(
                    DidChangeConfigurationParams().also { p -> p.settings = buildSettings() }
                )
            }
        }
    }

    private fun buildNextflowConfig(): JsonObject {
        val settings = NextflowSettings.getInstance(project)
        return JsonObject().apply {
            addProperty("errorReportingMode", settings.state.errorReportingMode)
            add("files", JsonObject().apply {
                add("exclude", JsonArray().apply {
                    add(".git")
                    add(".nf-test")
                    add("work")
                })
            })
            addProperty("languageVersion", "26.04")
            addProperty("debug", false)
            add("completion", JsonObject().apply {
                addProperty("extended", true)
                addProperty("maxItems", 50)
            })
            add("formatting", JsonObject().apply {
                addProperty("harshilAlignment", false)
                addProperty("sortDeclarations", false)
            })
        }
    }

    private fun buildSettings(): JsonObject {
        return JsonObject().apply {
            add("nextflow", buildNextflowConfig())
        }
    }

    override fun createInitializeParams(): org.eclipse.lsp4j.InitializeParams {
        return super.createInitializeParams().also { params ->
            val caps = params.capabilities ?: return@also
            // The Nextflow LSP server sends WorkDoneProgressBegin without the required
            // 'title' field, causing a NPE in IntelliJ's bundled LSP4J. Disabling
            // workDoneProgress tells the server to skip these notifications entirely.
            val window = caps.window ?: WindowClientCapabilities()
            window.workDoneProgress = false
            caps.window = window
        }
    }

    override fun startServerProcess(): OSProcessHandler {
        val commandLine = createCommandLine()
        val process = commandLine.createProcess()
        return NextflowPatchedProcessHandler(process, commandLine.commandLineString)
    }

    override val lspCommandsSupport = NextflowLspCommandsSupport()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NextflowLspServerDescriptor) return false
        return project == other.project
    }

    override fun hashCode(): Int = project.hashCode()

}