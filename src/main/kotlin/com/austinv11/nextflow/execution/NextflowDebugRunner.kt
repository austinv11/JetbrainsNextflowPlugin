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
import com.intellij.execution.runners.GenericProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger

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

        ApplicationManager.getApplication().invokeLater {
            attachDebugger(environment, port)
        }

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
