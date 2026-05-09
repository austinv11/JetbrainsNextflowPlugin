package com.austinv11.nextflow.inspections

import com.austinv11.nextflow.util.NextflowFileUtils
import com.austinv11.nextflow.NextflowSettings
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
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
        if (!NextflowFileUtils.isNextflowScript(file) && !NextflowFileUtils.isNextflowConfig(file)) {
            return null
        }

        // Check if user has ignored this warning for this project
        val settings = NextflowSettings.getInstance(project)
        if (settings.state.ignoreMissingFilesWarning) {
            return null
        }

        val projectDir = project.guessProjectDir() ?: return null

        val missingMain = projectDir.findChild("main.nf") == null
        val missingConfig = projectDir.findChild(NextflowFileUtils.NEXTFLOW_CONFIG_NAME) == null

        if (!missingMain && !missingConfig) {
            return null
        }

        return Function { editor: FileEditor ->
            val panel = EditorNotificationPanel(EditorNotificationPanel.Status.Warning)

            val missingNames = mutableListOf<String>()
            if (missingMain) missingNames.add("main.nf")
            if (missingConfig) missingNames.add(NextflowFileUtils.NEXTFLOW_CONFIG_NAME)

            val message = "This project is missing typical Nextflow files: " + missingNames.joinToString(" and ") + "."
            panel.text = message

            if (missingMain) {
                panel.createActionLabel("Create main.nf") {
                    createFileFromTemplateSilent(project, projectDir, "main", "Nextflow Script")
                    EditorNotifications.getInstance(project).updateAllNotifications()
                }
            }

            if (missingConfig) {
                panel.createActionLabel("Create nextflow.config") {
                    createFileFromTemplateSilent(project, projectDir, "nextflow", "Nextflow Config")
                    EditorNotifications.getInstance(project).updateAllNotifications()
                }
            }

            panel.createActionLabel("Don't show again") {
                settings.state.ignoreMissingFilesWarning = true
                EditorNotifications.getInstance(project).updateAllNotifications()
            }

            panel
        }
    }

    private fun createFileFromTemplateSilent(project: Project, projectDir: VirtualFile, fileName: String, templateName: String) {
        val psiManager = PsiManager.getInstance(project)
        val psiDir = psiManager.findDirectory(projectDir) ?: return

        val template = FileTemplateManager.getInstance(project).getInternalTemplate(templateName)

        val properties = Properties()
        FileTemplateManager.getInstance(project).defaultProperties.let { properties.putAll(it) }
        properties.setProperty(FileTemplateManager.PROJECT_NAME_VARIABLE, project.name)
        properties.setProperty("NAME", fileName)

        WriteCommandAction.runWriteCommandAction(project, "Create $fileName", null, {
            try {
                // 1. Capture the newly created PsiElement
                val createdElement = FileTemplateUtil.createFromTemplate(template, fileName, properties, psiDir)

                // 2. Extract the VirtualFile
                val virtualFile = createdElement.containingFile?.virtualFile

                // 3. Open the file in the editor, setting focus to true
                if (virtualFile != null) {
                    FileEditorManager.getInstance(project).openFile(virtualFile, true)
                }

            } catch (e: Exception) {
                // Ignore if it fails
            }
        })
    }
}
