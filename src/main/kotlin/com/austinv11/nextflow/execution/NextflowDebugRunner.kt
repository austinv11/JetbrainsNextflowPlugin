package com.austinv11.nextflow.execution

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.remote.RemoteConfiguration
import com.intellij.execution.remote.RemoteConfigurationType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import java.util.regex.Pattern

class NextflowDebugRunner : GenericProgramRunner<RunnerSettings>() {

    private val logger = Logger.getInstance(NextflowDebugRunner::class.java)

    override fun getRunnerId(): String = "NextflowDebugRunner"

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == DefaultDebugExecutor.EXECUTOR_ID && profile is NextflowRunConfiguration
    }

    @Throws(ExecutionException::class)
    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val executionResult = state.execute(environment.executor, this) ?: return null

        val processHandler = executionResult.processHandler

        processHandler.addProcessListener(object : ProcessAdapter() {
            private val debugPortPattern = Pattern.compile("Listening for transport dt_socket at address: (\\d+)")

            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                val text = event.text ?: return
                val matcher = debugPortPattern.matcher(text)
                if (matcher.find()) {
                    val portString = matcher.group(1)
                    val port = portString.toIntOrNull()
                    if (port != null) {
                        logger.info("Found Nextflow debug port: $port")
                        processHandler.removeProcessListener(this)

                        ApplicationManager.getApplication().invokeLater {
                            attachDebugger(environment, port)
                        }
                    }
                }
            }
        })

        return com.intellij.execution.runners.showRunContent(executionResult, environment)
    }

    private fun attachDebugger(environment: ExecutionEnvironment, port: Int) {
        val project = environment.project
        try {
            val remoteConfigType = RemoteConfigurationType.getInstance()
            val remoteConfigFactory = remoteConfigType.configurationFactories.first()
            val remoteConfig = RemoteConfiguration(project, remoteConfigFactory)

            val host = if ((environment.runProfile as? NextflowRunConfiguration)?.defaultTargetName != null) { "localhost" } else { "localhost" }
            remoteConfig.HOST = host
            remoteConfig.PORT = port.toString()
            remoteConfig.USE_SOCKET_TRANSPORT = true
            remoteConfig.SERVER_MODE = false

            val remoteEnvBuilder = com.intellij.execution.runners.ExecutionEnvironmentBuilder.create(
                environment.project,
                environment.executor,
                remoteConfig
            )

            val remoteEnv = remoteEnvBuilder.build()

            val remoteState = remoteConfig.getState(environment.executor, remoteEnv)

            if (remoteState != null) {
                val executionResult = remoteState.execute(environment.executor, this)
                if (executionResult != null) {
                    com.intellij.execution.runners.showRunContent(executionResult, remoteEnv)
                }
            }

        } catch (e: ExecutionException) {
            logger.error("Failed to attach remote debugger to port $port", e)
        }
    }
}
