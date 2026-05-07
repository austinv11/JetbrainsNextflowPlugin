package com.austinv11.nextflow.lsp

import com.austinv11.nextflow.NextflowSettings
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerListener
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.InitializeResult
import java.io.File

class NextflowLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "Nextflow LSP") {

    override fun isSupportedFile(file: VirtualFile): Boolean = file.extension == "nf" || file.name == "nextflow.config"

    override fun getLanguageId(file: VirtualFile): String = when {
        file.extension == "nf" -> "nextflow"
        file.name == "nextflow.config" -> "nextflow-config"
        else -> super.getLanguageId(file)
    }

    override fun createCommandLine(): GeneralCommandLine {
        val customPath = NextflowSettings.getInstance(project).state.customLspJarPath
        val serverJar = if (customPath.isNotBlank()) {
            File(customPath).takeIf { it.exists() }
                ?: throw RuntimeException("Custom Nextflow LSP JAR not found: $customPath")
        } else {
            NextflowLspServerDownloader.getOrDownloadLspServer()
                ?: throw RuntimeException("Failed to locate or download Nextflow LSP server JAR")
        }
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
        val state = NextflowSettings.getInstance(project).state
        return JsonObject().apply {
            addProperty("errorReportingMode", state.errorReportingMode)
            add("files", JsonObject().apply {
                add("exclude", JsonArray().apply {
                    state.filesExclude.split(",")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { add(it) }
                })
            })
            addProperty("languageVersion", state.languageVersion)
            addProperty("debug", state.debugMode)
            add("completion", JsonObject().apply {
                addProperty("extended", state.completionExtended)
                addProperty("maxItems", state.completionMaxItems)
            })
            add("formatting", JsonObject().apply {
                addProperty("harshilAlignment", state.formattingHarshilAlignment)
                addProperty("sortDeclarations", state.formattingSortDeclarations)
            })
        }
    }

    private fun buildSettings(): JsonObject {
        return JsonObject().apply {
            add("nextflow", buildNextflowConfig())
        }
    }

    override val lspCommandsSupport = NextflowLspCommandsSupport()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NextflowLspServerDescriptor) return false
        return project == other.project
    }

    override fun hashCode(): Int = project.hashCode()

}