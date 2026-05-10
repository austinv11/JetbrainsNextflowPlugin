package com.austinv11.nextflow.execution

import com.intellij.execution.ExecutionException
import com.intellij.util.net.NetUtils
import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.remote.RemoteConfiguration
import com.intellij.execution.remote.RemoteConfigurationType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.util.Key
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.execution.ExecutionManager

class NextflowDebugRunner : GenericProgramRunner<RunnerSettings>() {

    private val logger = Logger.getInstance(NextflowDebugRunner::class.java)

    override fun getRunnerId(): String = "NextflowDebugRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is NextflowRunConfiguration
    }

    @Throws(ExecutionException::class)
    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val port = NetUtils.tryToFindAvailableSocketPort()
        if (port == -1) {
            throw ExecutionException("Could not find an available port for debugging.")
        }

        logger.info("Allocated port $port for Nextflow remote debugging")
        environment.putUserData(NEXTFLOW_DEBUG_PORT_KEY, port)

        val executionResult = state.execute(environment.executor, this) ?: return null

        val processHandler = executionResult.processHandler
        processHandler?.addProcessListener(object : ProcessListener {
            private var attached = false
            private var buffer = StringBuilder()
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (attached) return

                // Strip ANSI escape codes
                val text = event.text.replace(Regex("\\x1B\\[[0-9;]*[a-zA-Z]"), "")
                buffer.append(text)

                val currentText = buffer.toString()
                if (currentText.contains("Listening for transport dt_socket at address:") || currentText.contains("Listening for transport dt_socket")) {
                    attached = true
                    ApplicationManager.getApplication().invokeLater {
                        attachDebugger(environment, port)
                    }
                } else if (buffer.length > 8192) {
                    // Truncate from the beginning but keep the last few hundred characters
                    buffer.delete(0, buffer.length - 1000)
                }
            }
            override fun startNotified(event: ProcessEvent) {}
            override fun processTerminated(event: ProcessEvent) {}
        })

        return com.intellij.execution.runners.showRunContent(executionResult, environment)
    }

    private fun attachDebugger(environment: ExecutionEnvironment, port: Int) {
        val project = environment.project
        try {
            val remoteConfigType = RemoteConfigurationType.getInstance()
            val remoteConfigFactory = remoteConfigType.configurationFactories.first()
            val remoteConfig = RemoteConfiguration(project, remoteConfigFactory)

            val host = "localhost"
            remoteConfig.HOST = host
            remoteConfig.PORT = port.toString()
            remoteConfig.USE_SOCKET_TRANSPORT = true
            remoteConfig.SERVER_MODE = false

            // Name the configuration dynamically to provide context
            remoteConfig.name = "Nextflow Auto-Attach Debug"

            val remoteEnvBuilder = com.intellij.execution.runners.ExecutionEnvironmentBuilder.create(
                environment.project,
                environment.executor,
                remoteConfig
            )

            val remoteEnv = remoteEnvBuilder.build()

            // We cannot use 'this' (NextflowDebugRunner) to execute RemoteConfiguration because canRun() fails.
            // We must find the default GenericDebuggerRunner.
            val runner = ProgramRunner.getRunner(DefaultDebugExecutor.EXECUTOR_ID, remoteConfig)
            if (runner == null) {
                logger.error("Could not find a valid ProgramRunner to execute the RemoteConfiguration.")
                return
            }

            // Ensure the remoteEnv runner is properly set
            runner.execute(remoteEnv)

        } catch (e: ExecutionException) {
            logger.error("Failed to attach remote debugger to port $port", e)
        }
    }
}