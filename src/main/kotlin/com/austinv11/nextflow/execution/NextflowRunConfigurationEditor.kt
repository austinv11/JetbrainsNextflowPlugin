package com.austinv11.nextflow.execution

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class NextflowRunConfigurationEditor : SettingsEditor<NextflowRunConfiguration>() {
    private val scriptPathField = TextFieldWithBrowseButton()
    private val entryNameField = JBTextField()
    private val parametersField = JBTextField()
    private val profilesField = JBTextField()
    private val workDirField = TextFieldWithBrowseButton()

    init {
        scriptPathField.addBrowseFolderListener(
            "Select Nextflow Script",
            "Select the main .nf file to run",
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor("nf"),
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
        )

        workDirField.addBrowseFolderListener(
            "Select Working Directory",
            "Select the directory where Nextflow will execute",
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor(),
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
        )
    }

    override fun resetEditorFrom(config: NextflowRunConfiguration) {
        scriptPathField.text = config.scriptPath ?: ""
        entryNameField.text = config.entryName ?: ""
        parametersField.text = config.parameters ?: ""
        profilesField.text = config.profiles ?: ""
        workDirField.text = config.workDir ?: ""
    }

    override fun applyEditorTo(config: NextflowRunConfiguration) {
        config.scriptPath = scriptPathField.text
        config.entryName = entryNameField.text
        config.parameters = parametersField.text
        config.profiles = profilesField.text
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
            row("Profiles:") {
                cell(profilesField).align(AlignX.FILL)
            }
            row("Working directory:") {
                cell(workDirField).align(AlignX.FILL)
            }
        }
    }
}
