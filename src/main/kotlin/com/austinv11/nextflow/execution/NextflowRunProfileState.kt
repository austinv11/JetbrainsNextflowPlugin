package com.austinv11.nextflow.execution

import com.austinv11.nextflow.NextflowSettings
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.util.SystemInfo
import java.io.File

class NextflowRunProfileState(
    environment: ExecutionEnvironment,
    private val configuration: NextflowRunConfiguration
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val nextflowBin = NextflowSettings.getInstance(environment.project).state.nextflowBinaryPath.takeIf { it.isNotBlank() } ?: "nextflow"
        
        val commandLine = if (SystemInfo.isWindows) {
            GeneralCommandLine("wsl", nextflowBin)
        } else {
            GeneralCommandLine(nextflowBin)
        }

        // Set working directory
        val workDir = configuration.workDir.takeIf { !it.isNullOrBlank() } ?: environment.project.basePath
        if (workDir != null) {
            commandLine.withWorkDirectory(workDir)
        }

        // Check if debugging is requested
        if (environment.isDebug()) {
            commandLine.addParameter("-remote-debug")
        }

        commandLine.addParameter("run")

        val scriptPath = configuration.scriptPath
        if (scriptPath.isNullOrBlank()) {
            throw ExecutionException("Script path is not specified.")
        }
        
        // If on windows and using WSL, we need to convert the path, but for simplicity let's rely on standard path resolution 
        // or assume the user sets a valid WSL path if they are on Windows.
        commandLine.addParameter(scriptPath)

        val entryName = configuration.entryName
        if (!entryName.isNullOrBlank()) {
            commandLine.addParameter("-entry")
            commandLine.addParameter(entryName)
        }

        val profiles = configuration.profiles
        if (!profiles.isNullOrBlank()) {
            commandLine.addParameter("-profile")
            commandLine.addParameter(profiles)
        }

        val parameters = configuration.parameters
        if (!parameters.isNullOrBlank()) {
            // Primitive split by space for now
            val paramsList = parameters.split(" ").filter { it.isNotBlank() }
            commandLine.addParameters(paramsList)
        }

        val processHandler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine)
        return processHandler
    }

    private fun ExecutionEnvironment.isDebug(): Boolean {
        return this.runner.runnerId == "NextflowDebugRunner" // We will create this runner
    }
}
