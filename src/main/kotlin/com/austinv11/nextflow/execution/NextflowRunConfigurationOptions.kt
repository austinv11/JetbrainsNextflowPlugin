package com.austinv11.nextflow.execution

import com.intellij.execution.configurations.LocatableRunConfigurationOptions

class NextflowRunConfigurationOptions : LocatableRunConfigurationOptions() {
    private val scriptPathOption = string("").provideDelegate(this, "scriptPath")
    private val entryNameOption = string("").provideDelegate(this, "entryName")
    private val parametersOption = string("").provideDelegate(this, "parameters")
    private val profilesOption = string("").provideDelegate(this, "profiles")
    private val workDirOption = string("").provideDelegate(this, "workDir")

    var scriptPath: String?
        get() = scriptPathOption.getValue(this)
        set(value) = scriptPathOption.setValue(this, value ?: "")

    var entryName: String?
        get() = entryNameOption.getValue(this)
        set(value) = entryNameOption.setValue(this, value ?: "")

    var parameters: String?
        get() = parametersOption.getValue(this)
        set(value) = parametersOption.setValue(this, value ?: "")

    var profiles: String?
        get() = profilesOption.getValue(this)
        set(value) = profilesOption.setValue(this, value ?: "")

    var workDir: String?
        get() = workDirOption.getValue(this)
        set(value) = workDirOption.setValue(this, value ?: "")
}
