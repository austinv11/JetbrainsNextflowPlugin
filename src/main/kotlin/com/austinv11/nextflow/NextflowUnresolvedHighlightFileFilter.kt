package com.austinv11.nextflow

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.extensions.GroovyUnresolvedHighlightFileFilter

class NextflowUnresolvedHighlightFileFilter : GroovyUnresolvedHighlightFileFilter() {
    override fun isReject(file: PsiFile): Boolean =
        file.virtualFile?.let { it.extension == "nf" || it.name == "nextflow.config" } ?: false
}