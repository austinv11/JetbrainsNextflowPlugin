package com.austinv11.nextflow.lsp

import com.intellij.platform.lsp.api.customization.LspCustomization

class NextflowLspCustomization : LspCustomization() {
    override val commandsCustomizer = NextflowLspCommandsSupport()
    override val completionCustomizer = NextflowLspCompletionSupport()
}
