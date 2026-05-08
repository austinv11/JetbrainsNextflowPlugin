package com.austinv11.nextflow

import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiWhiteSpace

class NextflowLanguageInjector : MultiHostInjector {
    companion object {
        private val BLOCK_NAMES = setOf("script", "shell", "exec")
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> {
        return listOf(PsiLanguageInjectionHost::class.java)
    }

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        // We only care about string literals
        val node = context.node ?: return
        val elementType = node.elementType.toString()
        if (!elementType.contains("string", ignoreCase = true) && !elementType.contains("literal", ignoreCase = true)) return

        // Walk backwards to find the block identifier and colon
        var current: PsiElement? = context.prevSibling
        var foundColon = false
        var blockName = ""

        while (current != null) {
            val currentType = current.node?.elementType?.toString()
            if (current is PsiWhiteSpace || (currentType != null && (current.text.isBlank() || currentType == "new line"))) {
                current = current.prevSibling
                continue
            }

            if (!foundColon) {
                if (current.text == ":") {
                    foundColon = true
                    current = current.prevSibling
                    continue
                } else {
                    // Stop searching if we hit something else before the colon
                    return
                }
            } else {
                if (currentType == "identifier") {
                    blockName = current.text
                    break
                } else {
                    return
                }
            }
        }

        if (blockName in BLOCK_NAMES) {
            val bashLanguage = Language.findLanguageByID("Bash") ?: Language.findLanguageByID("sh") ?: return

            // Handle quotes properly to only inject into the content
            val text = context.text
            val prefixLength = when {
                text.startsWith("'''") || text.startsWith("\"\"\"") -> 3
                text.startsWith("'") || text.startsWith("\"") -> 1
                else -> 0
            }
            val suffixLength = when {
                text.length >= 3 && (text.endsWith("'''") || text.endsWith("\"\"\"")) -> 3
                text.length >= 1 && text.substring(prefixLength).let { it.endsWith("'") || it.endsWith("\"") } -> 1
                else -> 0
            }

            // Only inject if there's actual content inside the string, ensuring no TextRange errors
            // and avoiding open registrars
            if (text.length >= prefixLength + suffixLength && text.length - suffixLength > prefixLength) {
                registrar.startInjecting(bashLanguage)
                registrar.addPlace(null, null, context as PsiLanguageInjectionHost, TextRange(prefixLength, text.length - suffixLength))
                registrar.doneInjecting()
            }
        }
    }
}
