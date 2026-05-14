package com.austinv11.nextflow.util

import com.austinv11.nextflow.NextflowSettings
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo

object NextflowEnvironmentUtils {

    /**
     * Checks if the Groovy plugin is available and enabled in the IDE.
     */
    val isGroovyAvailable by lazy {
        try {
            PluginManagerCore.getPlugin(PluginId.getId("org.intellij.groovy"))?.let { !PluginManagerCore.isDisabled(it.pluginId) } == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Retrieves the raw Nextflow binary path from settings, falling back to "nextflow" if empty.
     */
    fun getNextflowBinary(project: Project): String {
        return NextflowSettings.getInstance(project).state.nextflowBinaryPath.takeIf { it.isNotBlank() } ?: "nextflow"
    }

    /**
     * Retrieves the executable command string for Nextflow, prepending "wsl " on Windows.
     */
    fun getExecutableNextflowCommand(project: Project): String {
        val bin = getNextflowBinary(project)
        return if (SystemInfo.isWindows) "wsl $bin" else bin
    }


    /**
     * Retrieves the raw nf-core binary path from settings, falling back to "nf-core" if empty.
     */
    fun getNfCoreBinary(project: Project): String {
        return NextflowSettings.getInstance(project).state.nfCoreBinaryPath.takeIf { it.isNotBlank() } ?: "nf-core"
    }

    /**
     * Retrieves the executable command string for nf-core, prepending "wsl " on Windows.
     */
    fun getExecutableNfCoreCommand(project: Project): String {
        val bin = getNfCoreBinary(project)
        return if (SystemInfo.isWindows) "wsl $bin" else bin
    }

    /**
     * Retrieves the Java executable path from settings, falling back to the system's java.home.
     */
    fun getJavaExecutable(project: Project): String {
        val javaHome = NextflowSettings.getInstance(project).state.javaHome
        return if (javaHome.isNotBlank()) {
            "$javaHome/bin/java"
        } else {
            System.getProperty("java.home") + "/bin/java"
        }
    }

    /**
     * Converts a Windows path to a WSL path if running on Windows.
     * e.g., C:/path/to/file -> /mnt/c/path/to/file
     */
    fun convertToWslPathIfNeeded(path: String): String {
        if (!SystemInfo.isWindows) return path

        // Regex to find things like C:/ or C:\, optionally preceded by '=' or spaces (e.g. --input=C:/...)
        val regex = Regex("""(^|[^a-zA-Z0-9])([a-zA-Z]):[/\\]""")
        var result = path
        // Apply replace and normalise slashes if modified
        result = result.replace(regex) { matchResult ->
            val prefix = matchResult.groupValues[1]
            val drive = matchResult.groupValues[2].lowercase()
            "$prefix/mnt/$drive/"
        }

        // Only replace backslashes if the string actually looks like a path and was processed or we are on windows
        // To be safe we just replace all backslashes with forward slashes since WSL only uses forward slashes
        return result.replace('\\', '/')
    }

    /**
     * Converts a WSL path back to a Windows path if running on Windows.
     * e.g., /mnt/c/path/to/file -> C:\path\to\file
     */
    fun convertFromWslPathIfNeeded(path: String): String {
        if (!SystemInfo.isWindows) return path

        val regex = Regex("""^/mnt/([a-zA-Z])/(.*)$""")
        val match = regex.find(path) ?: return path

        val drive = match.groupValues[1].uppercase()
        val rest = match.groupValues[2]
        return "$drive:\\${rest.replace('/', '\\')}"
    }
}
