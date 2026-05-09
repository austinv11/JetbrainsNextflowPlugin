package com.austinv11.nextflow.execution

import com.austinv11.nextflow.NextflowSettings
import com.austinv11.nextflow.util.NextflowEnvironmentUtils
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.util.SystemInfo
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentAwareRunProfileState
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Key

val NEXTFLOW_DEBUG_PORT_KEY = Key.create<Int>("NEXTFLOW_DEBUG_PORT")


class NextflowRunProfileState(
    environment: ExecutionEnvironment,
    private val configuration: NextflowRunConfiguration
) : CommandLineState(environment), TargetEnvironmentAwareRunProfileState {

    private var targetCommandLineBuilder: TargetedCommandLineBuilder? = null

    override fun prepareTargetEnvironmentRequest(
        targetEnvironmentRequest: TargetEnvironmentRequest,
        targetProgressIndicator: com.intellij.execution.target.TargetProgressIndicator
    ) {
        val targetedCommandLineBuilder = TargetedCommandLineBuilder(targetEnvironmentRequest)

        val nextflowBin = NextflowEnvironmentUtils.getNextflowBinary(environment.project)
        targetedCommandLineBuilder.setExePath(nextflowBin)
        if (environment.isDebug()) {
            targetedCommandLineBuilder.addParameter("-remote-debug")
            val debugPort = environment.getUserData(NEXTFLOW_DEBUG_PORT_KEY)
            if (debugPort != null) {
                targetedCommandLineBuilder.addEnvironmentVariable("NXF_REMOTE_DEBUG_PORT", debugPort.toString())
            }
        }

        targetedCommandLineBuilder.addParameter("run")

        val scriptPath = configuration.scriptPath
        if (scriptPath.isNullOrBlank()) {
            throw ExecutionException("Script path is not specified.")
        }

        targetedCommandLineBuilder.addParameter(scriptPath)

        val entryName = configuration.entryName
        if (!entryName.isNullOrBlank()) {
            targetedCommandLineBuilder.addParameter("-entry")
            targetedCommandLineBuilder.addParameter(entryName)
        }

        val profiles = configuration.profiles
        if (!profiles.isNullOrBlank()) {
            targetedCommandLineBuilder.addParameter("-profile")
            targetedCommandLineBuilder.addParameter(profiles)
        }

        val parameters = configuration.parameters
        if (!parameters.isNullOrBlank()) {
            val paramsList = parameters.split(" ").filter { it.isNotBlank() }
            paramsList.forEach { targetedCommandLineBuilder.addParameter(it) }
        }

        val arguments = configuration.arguments
        if (!arguments.isNullOrBlank()) {
            val argsList = arguments.split(" ").filter { it.isNotBlank() }
            argsList.forEach { targetedCommandLineBuilder.addParameter(it) }
        }

        this.targetCommandLineBuilder = targetedCommandLineBuilder
    }

    override fun handleCreatedTargetEnvironment(
        targetEnvironment: com.intellij.execution.target.TargetEnvironment,
        targetProgressIndicator: com.intellij.execution.target.TargetProgressIndicator
    ) {
        // Setup done after environment is built
    }

    override fun startProcess(): ProcessHandler {
        val targetedBuilder = targetCommandLineBuilder
        if (targetedBuilder != null) {
            throw ExecutionException("Nextflow Target environment execution requires starting via TargetEnvironmentRunner in newer IntelliJ versions.")
        }
        return startLocalProcess()
    }

    private fun startLocalProcess(): ProcessHandler {
        val nextflowBin = NextflowEnvironmentUtils.getNextflowBinary(environment.project)

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
            val debugPort = environment.getUserData(NEXTFLOW_DEBUG_PORT_KEY)
            if (debugPort != null) {
                commandLine.withEnvironment("NXF_REMOTE_DEBUG_PORT", debugPort.toString())
                if (SystemInfo.isWindows) {
                    val existingWslEnv = commandLine.environment["WSLENV"] ?: ""
                    val wslEnvAddition = if (existingWslEnv.isEmpty()) "NXF_REMOTE_DEBUG_PORT/u" else "$existingWslEnv:NXF_REMOTE_DEBUG_PORT/u"
                    commandLine.withEnvironment("WSLENV", wslEnvAddition)
                }
            }
        }


        commandLine.addParameter("run")

        val scriptPath = configuration.scriptPath
        if (scriptPath.isNullOrBlank()) {
            throw ExecutionException("Script path is not specified.")
        }

        commandLine.addParameter(NextflowEnvironmentUtils.convertToWslPathIfNeeded(scriptPath))

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
            commandLine.addParameters(paramsList.map { NextflowEnvironmentUtils.convertToWslPathIfNeeded(it) })
        }

        val arguments = configuration.arguments
        if (!arguments.isNullOrBlank()) {
            val argsList = arguments.split(" ").filter { it.isNotBlank() }
            commandLine.addParameters(argsList)
        }

        return ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine)
    }

    private fun ExecutionEnvironment.isDebug(): Boolean {
        return this.runner.runnerId == "NextflowDebugRunner" // We will create this runner
    }
}
