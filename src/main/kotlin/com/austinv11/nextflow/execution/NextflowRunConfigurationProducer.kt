package com.austinv11.nextflow.execution

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
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
                var sibling = element.nextSibling
                while (sibling is PsiWhiteSpace) {
                    sibling = sibling.nextSibling
                }
                if (sibling is LeafPsiElement && sibling.text.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))) {
                    configuration.entryName = sibling.text
                    configuration.name = "Nextflow: ${file.name} - ${sibling.text}"
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
                 var sibling = element.nextSibling
                 while (sibling is PsiWhiteSpace) {
                     sibling = sibling.nextSibling
                 }
                 if (sibling is LeafPsiElement && sibling.text.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*"))) {
                     return configuration.entryName == sibling.text
                 } else {
                     return configuration.entryName.isNullOrBlank()
                 }
            }
        }

        return configuration.entryName.isNullOrBlank()
    }
}
