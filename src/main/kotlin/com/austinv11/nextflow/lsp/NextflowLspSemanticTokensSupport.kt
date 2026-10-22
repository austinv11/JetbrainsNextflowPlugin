package com.austinv11.nextflow.lsp

import com.austinv11.nextflow.util.NextflowFileUtils
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport
import com.intellij.psi.PsiFile
import org.eclipse.lsp4j.SemanticTokenTypes

class NextflowLspSemanticTokensSupport : LspSemanticTokensSupport() {
    override fun getTextAttributesKey(tokenType: String, modifiers: List<String>): TextAttributesKey? {
        // Semantic string spans cover script bodies and otherwise overwrite embedded Bash.
        // Both native and TextMate syntax highlighting already color ordinary strings.
        return if (tokenType == SemanticTokenTypes.String) null else super.getTextAttributesKey(tokenType, modifiers)
    }

    // The platform default only requests tokens for plain-text and TextMate PSI.
    // Match the server's files regardless of which optional parser handles them.
    override fun shouldAskServerForSemanticTokens(psiFile: PsiFile): Boolean {
        val file = psiFile.virtualFile
        return if (file != null) NextflowFileUtils.isNextflowLspFile(psiFile.project, file)
        else NextflowFileUtils.isNextflowScript(psiFile.name) || NextflowFileUtils.isNextflowConfig(psiFile.name)
    }
}
