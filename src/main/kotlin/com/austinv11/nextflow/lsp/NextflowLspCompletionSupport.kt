package com.austinv11.nextflow.lsp

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiFile
import com.intellij.platform.lsp.api.customization.LspCompletionSupport
import org.eclipse.lsp4j.CompletionItem

class NextflowLspCompletionSupport : LspCompletionSupport() {
    private val log = Logger.getInstance(NextflowLspCompletionSupport::class.java)

    override fun shouldRunCodeCompletion(parameters: CompletionParameters): Boolean {
        val file = parameters.originalFile
        val eligible = isNextflowFile(file)
        if (log.isDebugEnabled) {
            log.debug("LSP completion check: file=${file.name}, language=${file.language.id}, eligible=$eligible")
        }
        return eligible
    }

    override fun createLookupElement(
        parameters: CompletionParameters,
        item: CompletionItem
    ): LookupElement? {
        if (log.isDebugEnabled) {
            log.debug("LSP completion item: ${item.label}")
        }
        return super.createLookupElement(parameters, item)
    }

    private fun isNextflowFile(file: PsiFile): Boolean {
        val vf = file.virtualFile
        if (vf?.extension == "nf") return true
        if (vf?.name == "nextflow.config") return true
        return file.language.id == "Nextflow"
    }
}
