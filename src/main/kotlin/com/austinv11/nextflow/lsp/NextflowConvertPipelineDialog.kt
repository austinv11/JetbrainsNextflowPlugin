package com.austinv11.nextflow.lsp

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import javax.swing.JComponent

class NextflowConvertPipelineDialog(project: Project, initialFolder: String) : DialogWrapper(project) {

    private val folderField = TextFieldWithBrowseButton().apply {
        text = initialFolder
        addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Select Pipeline Folder")
                .withDescription("Choose the folder containing the Nextflow pipeline to convert")
        )
    }

    init {
        title = "Convert Pipeline to Static Types"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row("Pipeline Folder:") {
                cell(folderField).focused()
            }
        }.apply {
            preferredSize = Dimension(400, 50)
        }
    }

    fun getSelectedFolder(): String = folderField.text
}