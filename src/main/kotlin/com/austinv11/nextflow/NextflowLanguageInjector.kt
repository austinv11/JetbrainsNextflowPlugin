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
        private val BLOCK_NAMES = setOf("script", "shell")
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
        var attempts = 0

        while (current != null && attempts < 20) {
            attempts++
            val currentType = current.node?.elementType?.toString()
            val text = current.text

            if (current is PsiWhiteSpace || text.isBlank() || currentType == "new line") {
                current = current.prevSibling
                continue
            }

            if (!foundColon) {
                if (text == ":") {
                    foundColon = true
                }
                current = current.prevSibling
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
            val bashLanguage = Language.findLanguageByID("Shell Script") ?: return

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

            val contentStartIndex = prefixLength
            val contentEndIndex = text.length - suffixLength

            if (contentEndIndex > contentStartIndex) {
                registrar.startInjecting(bashLanguage)

                var currentOffset = contentStartIndex

                // Look for ${...} and !{...}
                var searchIndex = currentOffset
                while (searchIndex < contentEndIndex) {
                    val char = text[searchIndex]
                    if ((char == '$' || char == '!') && searchIndex + 1 < contentEndIndex && text[searchIndex + 1] == '{') {
                        // Found start of variable
                        val varStartIndex = searchIndex
                        var varEndIndex = -1
                        // Simple brace matching - this won't handle nested braces inside the Nextflow variable perfectly,
                        // but is generally sufficient for standard variables.
                        for (i in varStartIndex + 2 until contentEndIndex) {
                            if (text[i] == '}') {
                                varEndIndex = i + 1
                                break
                            }
                        }

                        if (varEndIndex != -1) {
                            if (varStartIndex > currentOffset) {
                                registrar.addPlace(null, null, context as PsiLanguageInjectionHost, TextRange(currentOffset, varStartIndex))
                            }
                            currentOffset = varEndIndex
                            searchIndex = varEndIndex - 1 // Will be incremented at the end of loop
                        }
                    }
                    searchIndex++
                }

                if (currentOffset < contentEndIndex) {
                    registrar.addPlace(null, null, context as PsiLanguageInjectionHost, TextRange(currentOffset, contentEndIndex))
                }

                registrar.doneInjecting()
            }
        }
    }
}
