package com.austinv11.nextflow.execution

import com.austinv11.nextflow.NextflowIcons
import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement

class NextflowRunLineMarkerContributor : RunLineMarkerContributor() {

    override fun getInfo(element: PsiElement): Info? {
        if (element !is LeafPsiElement) return null

        val text = element.text
        if (text != "workflow" && text != "process") return null

        // Need to check if this is genuinely a definition and not just a random word.
        // E.g., `workflow {` or `workflow foo {` or `process foo {`
        var sibling: PsiElement? = element.nextSibling
        var name: String? = null

        // Skip whitespace
        while (sibling is PsiWhiteSpace) {
            sibling = sibling.nextSibling
        }

        if (sibling is LeafPsiElement) {
            val siblingText = sibling.text
            if (siblingText == "{") {
                // It's an unnamed workflow
            } else if (siblingText.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))) {
                // Potential name
                name = siblingText
                sibling = sibling.nextSibling
                while (sibling is PsiWhiteSpace) {
                    sibling = sibling.nextSibling
                }
                if (sibling !is LeafPsiElement || sibling.text != "{") {
                    return null // Doesn't follow the pattern `name {`
                }
            } else {
                return null // Doesn't follow pattern `{` or `name {`
            }
        } else {
            return null
        }

        // It is a valid definition
        val actions = ExecutorAction.getActions(1)
        val tooltip = if (name != null) "Run $text '$name'" else "Run $text"
        return Info(
            AllIcons.RunConfigurations.TestState.Run,
            actions,
            { tooltip }
        )
    }
}
