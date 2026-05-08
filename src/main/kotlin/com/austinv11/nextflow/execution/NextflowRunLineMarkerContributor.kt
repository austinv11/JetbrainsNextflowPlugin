package com.austinv11.nextflow.execution

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.execution.lineMarker.RunLineMarkerContributor

import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.execution.lineMarker.RunLineMarkerContributor.Info
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement

class NextflowRunLineMarkerContributor : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is LeafPsiElement) return null

        val text = element.text
        if (text != "workflow" && text != "process") return null

        // In Groovy/Nextflow flat PSI, "def workflow = 1" or "workflow_foo" could contain text "workflow".
        // But since this is a leaf element from the parser, it should be matched exactly. We can verify
        // it is not just a substring because `text` is exactly "workflow" or "process".
        // However, we should be careful about variables named workflow: "def workflow" -> workflow is an identifier.
        // Usually, definitions are at the root level, but let's just trace ahead.

        var sibling: PsiElement? = element.nextSibling
        var name: String? = null

        // Skip whitespace, newlines, and comments
        while (sibling is PsiWhiteSpace || sibling is PsiComment || (sibling is LeafPsiElement && sibling.text.trim().isEmpty())) {
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
                while (sibling is PsiWhiteSpace || sibling is PsiComment || (sibling is LeafPsiElement && sibling.text.trim().isEmpty())) {
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

        // Check if preceding is `def` to avoid `def workflow = 1`
        var prevSibling = element.prevSibling
        while (prevSibling is PsiWhiteSpace || prevSibling is PsiComment || (prevSibling is LeafPsiElement && prevSibling.text.trim().isEmpty())) {
            prevSibling = prevSibling.prevSibling
        }
        if (prevSibling is LeafPsiElement && prevSibling.text == "def") {
            return null
        }

        // It is a valid definition
        // 0 gets both Run and Debug actions, if supported.
        val actions = ExecutorAction.getActions(0)
        val tooltip = if (name != null) "Run $text '$name'" else "Run $text"
        val info = RunLineMarkerContributor.Info(
            AllIcons.RunConfigurations.TestState.Run,
            actions,
            { tooltip }
        )
        // Fallback to standard LineMarkerInfo which does support ExecutorAction manually maybe?
        // Wait, standard RunLineMarkerContributor just returns info and RunLineMarkerProvider builds the marker.
        // Let's just create a custom LineMarkerInfo and inject the run actions.
        return object : LineMarkerInfo<PsiElement>(
            element,
            element.textRange,
            info.icon,
            { tooltip },
            null,
            GutterIconRenderer.Alignment.CENTER,
            { tooltip }
        ) {
            override fun createGutterRenderer(): GutterIconRenderer {
                return object : com.intellij.codeInsight.daemon.LineMarkerInfo.LineMarkerGutterIconRenderer<PsiElement>(this) {
                    override fun getClickAction(): com.intellij.openapi.actionSystem.AnAction? = null
                    override fun isNavigateAction(): Boolean = true
                    override fun getPopupMenuActions(): com.intellij.openapi.actionSystem.ActionGroup {
                        return com.intellij.openapi.actionSystem.DefaultActionGroup(actions.toList())
                    }
                }
            }
        }
    }
}
