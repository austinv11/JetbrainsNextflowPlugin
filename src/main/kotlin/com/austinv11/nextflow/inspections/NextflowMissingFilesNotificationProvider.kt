package com.austinv11.nextflow.inspections

import com.austinv11.nextflow.NextflowSettings
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.ui.CreateFromTemplateDialog
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.util.function.Function
import javax.swing.JComponent
import java.util.Properties

class NextflowMissingFilesNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        // Only trigger on Nextflow files
        if (file.extension != "nf" && file.name != "nextflow.config") {
            return null
        }

        // Check if user has ignored this warning for this project
        val settings = NextflowSettings.getInstance(project)
        if (settings.state.ignoreMissingFilesWarning) {
            return null
        }

        val projectDir = project.guessProjectDir() ?: return null

        val missingMain = projectDir.findChild("main.nf") == null
        val missingConfig = projectDir.findChild("nextflow.config") == null

        if (!missingMain && !missingConfig) {
            return null
        }

        return Function { editor: FileEditor ->
            val panel = EditorNotificationPanel(EditorNotificationPanel.Status.Warning)

            val missingNames = mutableListOf<String>()
            if (missingMain) missingNames.add("main.nf")
            if (missingConfig) missingNames.add("nextflow.config")

            val message = "This project is missing typical Nextflow files: " + missingNames.joinToString(" and ") + "."
            panel.text = message

            if (missingMain) {
                panel.createActionLabel("Create main.nf") {
                    createFileFromTemplate(project, projectDir, "main", "Nextflow Script")
                }
            }

            if (missingConfig) {
                panel.createActionLabel("Create nextflow.config") {
                    createFileFromTemplate(project, projectDir, "nextflow", "Nextflow Config")
                }
            }

            panel.createActionLabel("Don't show again") {
                settings.state.ignoreMissingFilesWarning = true
                EditorNotifications.getInstance(project).updateAllNotifications()
            }

            panel
        }
    }

    private fun createFileFromTemplate(project: Project, projectDir: VirtualFile, fileName: String, templateName: String) {
        val psiManager = PsiManager.getInstance(project)
        val psiDir = psiManager.findDirectory(projectDir) ?: return

        val template = FileTemplateManager.getInstance(project).getInternalTemplate(templateName)

        val properties = Properties()
        FileTemplateManager.getInstance(project).defaultProperties.let { properties.putAll(it) }
        properties.setProperty(FileTemplateManager.PROJECT_NAME_VARIABLE, project.name)
        properties.setProperty("NAME", fileName)

        val dialog = CreateFromTemplateDialog(project, psiDir, template, null, properties)
        dialog.create()
    }
}
