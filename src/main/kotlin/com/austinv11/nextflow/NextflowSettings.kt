package com.austinv11.nextflow

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.State
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.util.concurrent.TimeUnit

@State(
    name = "NextflowSettings",
    storages = [Storage("nextflow-lsp.xml")]
)
@Service(Service.Level.PROJECT)
class NextflowSettings : PersistentStateComponent<NextflowSettings.State> {

    companion object {
        private val logger = Logger.getInstance(NextflowSettings::class.java)
        private const val DEFAULT_LANGUAGE_VERSION = "26.04"

        fun getInstance(project: Project): NextflowSettings =
            project.getService(NextflowSettings::class.java)
    }

    data class State(
        // Server
        var customLspJarPath: String = "",

        // Execution
        var nextflowBinaryPath: String = "",

        // nf-core
        var nfCoreBinaryPath: String = "",

        // Java Runtime
        var javaHome: String = "",

        // Diagnostics
        var errorReportingMode: String = "warnings",

        // Language
        var languageVersion: String = DEFAULT_LANGUAGE_VERSION,

        // Completion
        var completionExtended: Boolean = true,
        var completionMaxItems: Int = 50,

        // Formatting
        var formattingHarshilAlignment: Boolean = false,
        var formattingMaheshForm: Boolean = false,
        var formattingSortDeclarations: Boolean = false,

        // Files
        var filesExclude: String = ".git,.nf-test,work",

        // Advanced

        // Inspections
        var ignoreMissingFilesWarning: Boolean = false,

        var debugMode: Boolean = false,
    )

    private var myState = State()

    init {
        // Don't auto-detect during service initialization - it can block startup
        // Detection will happen lazily when user opens settings or when needed
        logger.info("NextflowSettings initialized (version detection deferred)")
    }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun detectInstalledVersion(binaryPath: String? = null): String? {
        val bin = binaryPath?.takeIf { it.isNotBlank() }
            ?: myState.nextflowBinaryPath.takeIf { it.isNotBlank() }
            ?: "nextflow"

        logger.info("detectInstalledVersion: Starting detection with binary: '$bin'")
        logger.info("detectInstalledVersion: OS is ${if (SystemInfo.isWindows) "Windows" else if (SystemInfo.isMac) "macOS" else "Linux"}")

        // On Windows, try native execution first, then WSL as fallback
        val commandsToTry = if (SystemInfo.isWindows) {
            listOf(
                listOf(bin, "-v"),  // Try native Windows execution first
                listOf("wsl", bin, "-v")  // Fall back to WSL
            )
        } else {
            listOf(listOf(bin, "-v"))
        }

        logger.info("detectInstalledVersion: Will try ${commandsToTry.size} command(s)")

        for ((index, command) in commandsToTry.withIndex()) {
            val commandStr = command.joinToString(" ")
            logger.info("detectInstalledVersion: Attempt ${index + 1}/${ commandsToTry.size}: $commandStr")

            val result = runCatching {
                val process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()
                logger.debug("Process started for: $commandStr")

                // Drain stdout on a separate thread to prevent pipe-buffer deadlock
                val outputBuffer = StringBuilder()
                val readerThread = Thread {
                    try {
                        process.inputStream.bufferedReader().forEachLine { outputBuffer.appendLine(it) }
                    } catch (_: Exception) {}
                }.also { it.isDaemon = true; it.start() }

                // WSL cold-start can be slow; give it more time
                val timeoutSeconds = if (command[0] == "wsl") 30L else 5L

                if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    logger.warn("Process timeout for: $commandStr (waited $timeoutSeconds seconds)")
                    process.destroyForcibly()
                    return@runCatching null
                }

                readerThread.join(2_000)

                val exitCode = process.exitValue()
                logger.debug("Process exit code: $exitCode for: $commandStr")

                val output = outputBuffer.toString().trim()
                logger.debug("Process output: $output")

                if (output.isBlank()) {
                    logger.warn("Empty output from: $commandStr")
                    return@runCatching null
                }

                val matcher = Regex("""(\d+)\.(\d+)""").find(output)
                val version = matcher?.let { "${it.groupValues[1]}.${it.groupValues[2]}" }

                if (version != null) {
                    logger.info("Detected version: $version")
                }
                version
            }.onFailure { throwable ->
                logger.warn("Exception executing $commandStr: ${throwable.message}", throwable)
            }.getOrNull()

            if (result != null) {
                logger.info("detectInstalledVersion: Successfully detected version $result")
                return result
            }
        }

        logger.warn("detectInstalledVersion: Failed to detect Nextflow version after trying all methods")
        return null
    }

    fun detectInstalledNfCoreVersion(binaryPath: String? = null): String? {
        val bin = binaryPath?.takeIf { it.isNotBlank() }
            ?: myState.nfCoreBinaryPath.takeIf { it.isNotBlank() }
            ?: "nf-core"

        logger.info("detectInstalledNfCoreVersion: Starting detection with binary: '$bin'")

        val commandsToTry = if (SystemInfo.isWindows) {
            listOf(
                listOf(bin, "--version"),
                listOf("wsl", bin, "--version")
            )
        } else {
            listOf(listOf(bin, "--version"))
        }

        for (command in commandsToTry) {
            val commandStr = command.joinToString(" ")
            val result = runCatching {
                val process = ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start()

                val outputBuffer = StringBuilder()
                val readerThread = Thread {
                    try {
                        process.inputStream.bufferedReader().forEachLine { outputBuffer.appendLine(it) }
                    } catch (_: Exception) {}
                }.also { it.isDaemon = true; it.start() }

                val timeoutSeconds = if (command[0] == "wsl") 30L else 5L

                if (!process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return@runCatching null
                }

                readerThread.join(2_000)

                val output = outputBuffer.toString().trim()
                if (output.isBlank()) return@runCatching null

                // nf-core, version 2.14.1
                val matcher = Regex("version\\\\s+([\\\\d.]+)").find(output)
                matcher?.let { it.groupValues[1] }
            }.getOrNull()

            if (result != null) return result
        }

        return null
    }
}