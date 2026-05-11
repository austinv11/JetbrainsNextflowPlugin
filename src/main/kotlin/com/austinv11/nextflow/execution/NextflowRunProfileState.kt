package com.austinv11.nextflow.execution

import com.austinv11.nextflow.NextflowSettings
import com.austinv11.nextflow.util.NextflowEnvironmentUtils
import com.austinv11.nextflow.execution.console.NextflowWeblogServer
import com.austinv11.nextflow.execution.console.NextflowConsoleView
import com.intellij.execution.ExecutionResult
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Key

val NEXTFLOW_DEBUG_PORT_KEY = Key.create<Int>("NEXTFLOW_DEBUG_PORT")

class NextflowRunProfileState(
    environment: ExecutionEnvironment,
    private val configuration: NextflowRunConfiguration
) : CommandLineState(environment) {


    private val weblogServer = NextflowWeblogServer()

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        weblogServer.start()

        val processHandler = startProcess()

        processHandler.addProcessListener(object : ProcessListener {
            override fun startNotified(event: ProcessEvent) {}
            override fun processTerminated(event: ProcessEvent) {
                weblogServer.stop()
            }
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {}
        })

        val console = createConsole(executor)
        val customConsole = NextflowConsoleView(environment.project, console!!)
        customConsole.attachToProcess(processHandler)

        // Pass events from server to custom console
        weblogServer.onEventReceived = { event ->
            customConsole.handleWeblogEvent(event)
        }

        return DefaultExecutionResult(customConsole, processHandler, *createActions(customConsole, processHandler, executor))
    }

    override fun startProcess(): ProcessHandler {
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

        commandLine.addParameter("-with-weblog")
        commandLine.addParameter("http://127.0.0.1:${weblogServer.port}")

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
