package com.austinv11.nextflow

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext

class NextflowFileReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // Broaden the pattern to PsiElement to cover LeafPsiElements from Groovy
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiElement::class.java),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext
                ): Array<PsiReference> {
                    val node = element.node ?: return PsiReference.EMPTY_ARRAY
                    val elementType = node.elementType.toString()

                    if (!elementType.contains("string", ignoreCase = true)) return PsiReference.EMPTY_ARRAY

                    // Walk backwards to verify pattern: "file" or "path" -> (optional parenthesis) -> string
                    var current: PsiElement? = element.prevSibling
                    var foundParen = false
                    var functionName = ""

                    while (current != null) {
                        val currentType = current.node?.elementType?.toString()
                        if (current is PsiWhiteSpace || (currentType != null && (current.text.isBlank() || currentType == "new line"))) {
                            current = current.prevSibling
                            continue
                        }

                        if (!foundParen && current.text == "(") {
                            foundParen = true
                            current = current.prevSibling
                            continue
                        } else if (currentType == "identifier") {
                            functionName = current.text
                            break
                        } else {
                            // Hit something else before an identifier
                            return PsiReference.EMPTY_ARRAY
                        }
                    }

                    if (functionName == "file" || functionName == "path") {
                        val text = element.text
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

                        // Protect against invalid text ranges for unclosed or empty strings
                        if (text.length >= prefixLength + suffixLength) {
                            val textRange = TextRange(prefixLength, text.length - suffixLength)
                            if (!textRange.isEmpty) {
                                val pathString = textRange.substring(text)
                                // Skip templated strings as they aren't static paths
                                if (pathString.contains("$")) return PsiReference.EMPTY_ARRAY

                                val refSet = FileReferenceSet(pathString, element, prefixLength, null, true)
                                @Suppress("UNCHECKED_CAST")
                                return refSet.allReferences as Array<PsiReference>
                            }
                        }
                    }

                    return PsiReference.EMPTY_ARRAY
                }
            }
        )
    }
}
