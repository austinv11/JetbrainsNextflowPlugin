package com.austinv11.nextflow

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor

class NextflowLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "Nextflow LSP") {

    override fun isSupportedFile(file: VirtualFile): Boolean = file.extension == "nf" || file.name == "nextflow.config"

    override fun createCommandLine(): GeneralCommandLine {
        val serverJar = NextflowLspServerDownloader.getOrDownloadLspServer()
            ?: throw RuntimeException("Failed to locate or download Nextflow LSP server JAR")

        // Construct the Java command to run the server jar
        val javaExe = System.getProperty("java.home") + "/bin/java"
        return GeneralCommandLine(javaExe, "-jar", serverJar.absolutePath)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NextflowLspServerDescriptor) return false
        return project == other.project
    }

    override fun hashCode(): Int = project.hashCode()
}