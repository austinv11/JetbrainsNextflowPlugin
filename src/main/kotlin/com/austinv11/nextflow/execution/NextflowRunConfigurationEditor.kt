package com.austinv11.nextflow.execution

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class NextflowRunConfigurationEditor(private val project: Project) : SettingsEditor<NextflowRunConfiguration>() {
    private val scriptPathField = TextFieldWithBrowseButton()
    private val entryNameField = JBTextField()
    private val parametersField = JBTextField()
    private val profilesField = JBTextField()
    private val argumentsField = JBTextField()
    private val workDirField = TextFieldWithBrowseButton()
    // To support Target Environments gracefully in standard settings editor without fragments:


    init {
        scriptPathField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor("nf")
                .withTitle("Select Nextflow Script")
                .withDescription("Select the main .nf file to run")
        )

        workDirField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Select Working Directory")
                .withDescription("Select the directory where Nextflow will execute")
        )
    }

    override fun resetEditorFrom(config: NextflowRunConfiguration) {
        scriptPathField.text = config.scriptPath ?: ""
        entryNameField.text = config.entryName ?: ""
        parametersField.text = config.parameters ?: ""
        profilesField.text = config.profiles ?: ""
        argumentsField.text = config.arguments ?: ""
        workDirField.text = config.workDir ?: ""
        // Wait, TargetEnvironmentsConfigurable doesn't expose a simple setter for the selected item
        // We'll use the default Extension mechanism for Run Configurations which automatically injects target selection.
    }

    override fun applyEditorTo(config: NextflowRunConfiguration) {
        config.scriptPath = scriptPathField.text
        config.entryName = entryNameField.text
        config.parameters = parametersField.text
        config.profiles = profilesField.text
        config.arguments = argumentsField.text
        config.workDir = workDirField.text
    }

    override fun createEditor(): JComponent {
        return panel {
            row("Script path:") {
                cell(scriptPathField).align(AlignX.FILL)
            }
            row("Entry name:") {
                cell(entryNameField).align(AlignX.FILL)
            }
            row("Parameters:") {
                cell(parametersField).align(AlignX.FILL)
            }
            row("Arguments:") {
                cell(argumentsField).align(AlignX.FILL)
            }
            row("Profiles:") {
                cell(profilesField).align(AlignX.FILL)
            }
            row("Working directory:") {
                cell(workDirField).align(AlignX.FILL)
            }
            // Usually the Run Target is injected automatically by IntelliJ at the bottom or top of the run configuration
            // if it implements TargetEnvironmentAwareRunProfile.
        }
    }
}
