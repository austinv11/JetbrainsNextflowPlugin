package com.austinv11.nextflow

import com.austinv11.nextflow.util.NextflowFileUtils
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class NextflowCompletionAutoPopupHandler : TypedHandlerDelegate() {
    private val log = com.intellij.openapi.diagnostic.Logger.getInstance(NextflowCompletionAutoPopupHandler::class.java)

    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (charTyped != '.' && charTyped != ':') return Result.CONTINUE
        if (!isNextflowFile(file)) return Result.CONTINUE

        if (log.isDebugEnabled) {
            log.debug("Auto-popup trigger: char='$charTyped', file=${file.name}, language=${file.language.id}")
        }
        editor.putUserData(AutoPopupController.ALWAYS_AUTO_POPUP, true)
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor, com.intellij.codeInsight.completion.CompletionType.BASIC, null)
        return Result.CONTINUE
    }

    private fun isNextflowFile(file: PsiFile): Boolean {
        val vf = file.virtualFile
        if (vf != null && NextflowFileUtils.isNextflowFile(vf)) return true
        return file.language.id == "Nextflow"
    }
}
