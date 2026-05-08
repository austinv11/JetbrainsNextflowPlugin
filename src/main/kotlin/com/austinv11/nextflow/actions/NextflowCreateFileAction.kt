package com.austinv11.nextflow.actions

import com.austinv11.nextflow.NextflowIcons
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory

class NextflowCreateFileAction : CreateFileFromTemplateAction(
    "Nextflow File",
    "Create new Nextflow file",
    NextflowIcons.FILE
), DumbAware {

    override fun buildDialog(
        project: Project,
        directory: PsiDirectory,
        builder: CreateFileFromTemplateDialog.Builder
    ) {
        builder
            .setTitle("New Nextflow File")
            .addKind("Script", NextflowIcons.FILE, "Nextflow Script")
            .addKind("Process", NextflowIcons.FILE, "Nextflow Process")
            .addKind("Config", NextflowIcons.FILE, "Nextflow Config")
            .addKind("Test", NextflowIcons.FILE, "Nextflow Test")
    }

    override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String?): String {
        return "Create Nextflow File $newName"
    }

    override fun createFile(name: String?, templateName: String?, dir: PsiDirectory?): com.intellij.psi.PsiFile? {
        var finalName = name ?: return null
        if (templateName == "Nextflow Test") {
            if (!finalName.endsWith(".nf.test")) {
                finalName = finalName.removeSuffix(".test").removeSuffix(".nf") + ".nf.test"
            }
        }
        return super.createFile(finalName, templateName, dir)
    }
}
