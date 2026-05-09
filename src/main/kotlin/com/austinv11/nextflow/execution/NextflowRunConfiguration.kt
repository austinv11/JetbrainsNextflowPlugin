package com.austinv11.nextflow.execution

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

class NextflowRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : LocatableConfigurationBase<NextflowRunConfigurationOptions>(project, factory, name) {

    var scriptPath: String?
        get() = options.scriptPath
        set(value) {
            options.scriptPath = value
        }

    var entryName: String?
        get() = options.entryName
        set(value) {
            options.entryName = value
        }

    var parameters: String?
        get() = options.parameters
        set(value) {
            options.parameters = value
        }

    var profiles: String?
        get() = options.profiles
        set(value) {
            options.profiles = value
        }

    var arguments: String?
        get() = options.arguments
        set(value) {
            options.arguments = value
        }

    var workDir: String?
        get() = options.workDir
        set(value) {
            options.workDir = value
        }

    override fun getOptions(): NextflowRunConfigurationOptions {
        return super.getOptions() as NextflowRunConfigurationOptions
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return NextflowRunConfigurationEditor(project)
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        return NextflowRunProfileState(environment, this)
    }

}
