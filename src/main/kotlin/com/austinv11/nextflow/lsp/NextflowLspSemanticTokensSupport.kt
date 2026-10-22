package com.austinv11.nextflow.lsp

import com.austinv11.nextflow.util.NextflowFileUtils
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport
import com.intellij.psi.PsiFile

class NextflowLspSemanticTokensSupport : LspSemanticTokensSupport() {
    // The platform default only requests tokens for plain-text and TextMate PSI.
    // Match the server's files regardless of which optional parser handles them.
    override fun shouldAskServerForSemanticTokens(psiFile: PsiFile): Boolean =
        NextflowFileUtils.isNextflowScript(psiFile.name) || NextflowFileUtils.isNextflowConfig(psiFile.name)
}
