package com.austinv11.nextflow.execution

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement

class NextflowRunConfigurationProducer : LazyRunConfigurationProducer<NextflowRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory {
        return NextflowRunConfigurationType.getInstance().configurationFactories[0]
    }

    override fun setupConfigurationFromContext(
        configuration: NextflowRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val element = sourceElement.get() ?: return false
        val file = element.containingFile ?: return false

        if (file.fileType.name != "Nextflow") return false

        configuration.name = "Nextflow: ${file.name}"
        configuration.scriptPath = file.virtualFile.path
        configuration.workDir = file.project.basePath

        // If clicked on a specific workflow/process, we try to extract the entry name
        if (element is LeafPsiElement) {
            val text = element.text
            if (text == "workflow" || text == "process") {
                // Ensure it is actually a definition and not just `def workflow = 1`
                var prevSibling = element.prevSibling
                while (prevSibling is PsiWhiteSpace || prevSibling is PsiComment || (prevSibling is LeafPsiElement && prevSibling.text.trim().isEmpty())) {
                    prevSibling = prevSibling.prevSibling
                }
                if (prevSibling is LeafPsiElement && prevSibling.text == "def") {
                    return true // return true to create a basic file runner, but without entry name
                }

                var sibling = element.nextSibling
                while (sibling is PsiWhiteSpace || sibling is PsiComment || (sibling is LeafPsiElement && sibling.text.trim().isEmpty())) {
                    sibling = sibling.nextSibling
                }

                if (sibling is LeafPsiElement) {
                    val siblingText = sibling.text
                    if (siblingText == "{") {
                        // Unnamed workflow, entry name remains empty
                    } else if (siblingText.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))) {
                        // Named workflow or process
                        var nextSibling = sibling.nextSibling
                        while (nextSibling is PsiWhiteSpace || nextSibling is PsiComment || (nextSibling is LeafPsiElement && nextSibling.text.trim().isEmpty())) {
                            nextSibling = nextSibling.nextSibling
                        }
                        if (nextSibling is LeafPsiElement && nextSibling.text == "{") {
                            configuration.entryName = siblingText
                            configuration.name = "Nextflow: ${file.name} - $siblingText"
                        }
                    }
                }
            }
        }

        return true
    }

    override fun isConfigurationFromContext(
        configuration: NextflowRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val element = context.psiLocation ?: return false
        val file = element.containingFile ?: return false

        if (configuration.scriptPath != file.virtualFile.path) return false

        if (element is LeafPsiElement) {
            val text = element.text
            if (text == "workflow" || text == "process") {
                var prevSibling = element.prevSibling
                while (prevSibling is PsiWhiteSpace || prevSibling is PsiComment || (prevSibling is LeafPsiElement && prevSibling.text.trim().isEmpty())) {
                    prevSibling = prevSibling.prevSibling
                }
                if (prevSibling is LeafPsiElement && prevSibling.text == "def") {
                    return configuration.entryName.isNullOrBlank()
                }

                var sibling = element.nextSibling
                while (sibling is PsiWhiteSpace || sibling is PsiComment || (sibling is LeafPsiElement && sibling.text.trim().isEmpty())) {
                    sibling = sibling.nextSibling
                }

                if (sibling is LeafPsiElement) {
                    val siblingText = sibling.text
                    if (siblingText == "{") {
                        return configuration.entryName.isNullOrBlank()
                    } else if (siblingText.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))) {
                        var nextSibling = sibling.nextSibling
                        while (nextSibling is PsiWhiteSpace || nextSibling is PsiComment || (nextSibling is LeafPsiElement && nextSibling.text.trim().isEmpty())) {
                            nextSibling = nextSibling.nextSibling
                        }
                        if (nextSibling is LeafPsiElement && nextSibling.text == "{") {
                            return configuration.entryName == siblingText
                        }
                    }
                }
            }
        }

        return configuration.entryName.isNullOrBlank()
    }
}
