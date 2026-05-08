package com.austinv11.nextflow.execution

import com.austinv11.nextflow.NextflowIcons
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project

class NextflowRunConfigurationType : ConfigurationTypeBase(
    "NextflowRunConfiguration",
    "Nextflow",
    "Run Nextflow Pipeline",
    NextflowIcons.FILE
) {
    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun getId(): String = "Nextflow"
            
            override fun createTemplateConfiguration(project: Project): RunConfiguration {
                return NextflowRunConfiguration(project, this, "Nextflow")
            }

            override fun getOptionsClass() = NextflowRunConfigurationOptions::class.java
        })
    }

    companion object {
        fun getInstance(): NextflowRunConfigurationType {
            return ConfigurationType.CONFIGURATION_TYPE_EP.findExtensionOrFail(NextflowRunConfigurationType::class.java)
        }
    }
}
