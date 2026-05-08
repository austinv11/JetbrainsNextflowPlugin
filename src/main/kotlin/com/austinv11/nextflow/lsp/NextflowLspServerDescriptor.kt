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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import org.eclipse.lsp4j.ConfigurationItem
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.SemanticTokensCapabilities
import org.eclipse.lsp4j.SemanticTokensClientCapabilitiesRequests
import org.eclipse.lsp4j.TokenFormat

import java.io.File

class NextflowLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "Nextflow LSP") {

    private val logger = Logger.getInstance(NextflowLspServerDescriptor::class.java)

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
        val nextflowBin = NextflowSettings.getInstance(project).state.nextflowBinaryPath.takeIf { it.isNotBlank() } ?: "nextflow"
        val actualBin = if (SystemInfo.isWindows) "wsl $nextflowBin" else nextflowBin
        val env = mutableMapOf<String, String>()
        env.putAll(System.getenv())
        env["NEXTFLOW_BIN"] = actualBin
        val javaHome = NextflowSettings.getInstance(project).state.javaHome
        val javaExe = if (javaHome.isNotBlank()) {
            "$javaHome/bin/java"
        } else {
            System.getProperty("java.home") + "/bin/java"
        }
        return GeneralCommandLine(javaExe, "-jar", serverJar.absolutePath).withEnvironment(env)
    }

    override fun getWorkspaceConfiguration(item: ConfigurationItem): Any? {
        if (item.section == "nextflow") {
            return buildNextflowConfig()
        }
        return null
    }

    override fun createInitializeParams(): InitializeParams {
        val params = super.createInitializeParams()
        val capabilities = params.capabilities ?: org.eclipse.lsp4j.ClientCapabilities()

        val textDocument = capabilities.textDocument ?: org.eclipse.lsp4j.TextDocumentClientCapabilities()

        val completion = textDocument.completion ?: org.eclipse.lsp4j.CompletionCapabilities()
        val completionItem = completion.completionItem ?: org.eclipse.lsp4j.CompletionItemCapabilities()
        completionItem.snippetSupport = true
        completionItem.documentationFormat = listOf(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT)
        completion.completionItem = completionItem
        completion.contextSupport = true
        textDocument.completion = completion

        val hover = textDocument.hover ?: org.eclipse.lsp4j.HoverCapabilities()
        hover.contentFormat = listOf(MarkupKind.MARKDOWN, MarkupKind.PLAINTEXT)
        textDocument.hover = hover

        val semanticTokens = textDocument.semanticTokens ?: SemanticTokensCapabilities()
        semanticTokens.requests = SemanticTokensClientCapabilitiesRequests().apply { setFull(true) }
        semanticTokens.tokenTypes = emptyList()
        semanticTokens.tokenModifiers = emptyList()
        semanticTokens.formats = listOf(TokenFormat.Relative)
        semanticTokens.augmentsSyntaxTokens = true
        textDocument.semanticTokens = semanticTokens

        capabilities.textDocument = textDocument

        val workspace = capabilities.workspace ?: org.eclipse.lsp4j.WorkspaceClientCapabilities()
        val watchedFiles = workspace.didChangeWatchedFiles ?: org.eclipse.lsp4j.DidChangeWatchedFilesCapabilities()
        watchedFiles.dynamicRegistration = true
        workspace.didChangeWatchedFiles = watchedFiles
        capabilities.workspace = workspace

        params.capabilities = capabilities
        return params
    }

    override val lspServerListener = object : LspServerListener {
        override fun serverInitialized(params: InitializeResult) {
            val formatting = params.capabilities?.documentFormattingProvider ?: false
            val rangeFormatting = params.capabilities?.documentRangeFormattingProvider ?: false
            logger.info("Nextflow LSP formatting support: documentFormattingProvider=$formatting, documentRangeFormattingProvider=$rangeFormatting")

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
                addProperty("maheshForm", state.formattingMaheshForm)
            })
            add("telemetry", JsonObject().apply {
                addProperty("enabled", false)
            })
        }
    }

    private fun buildSettings(): JsonObject {
        return JsonObject().apply {
            add("nextflow", buildNextflowConfig())
        }
    }

    override val lspCommandsSupport = NextflowLspCommandsSupport()
    override val lspCompletionSupport = NextflowLspCompletionSupport()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NextflowLspServerDescriptor) return false
        return project == other.project
    }

    override fun hashCode(): Int = project.hashCode()

}