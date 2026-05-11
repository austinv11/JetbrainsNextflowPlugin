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
        private val QUOTES = setOf("'''", "\"\"\"", "'", "\"")
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> {
        return listOf(PsiLanguageInjectionHost::class.java)
    }

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        val node = context.node ?: return
        val elementType = node.elementType.toString()
        if (!elementType.contains("string", ignoreCase = true) && !elementType.contains("literal", ignoreCase = true)) return

        // 1. Walk backwards to find script/shell colon, and ensure this is the first CONTENT-BEARING string part.
        var current: PsiElement? = context.prevSibling
        var foundColon = false
        var blockName = ""
        var attempts = 0
        var quoteType = ""

        // Try to guess the quote type from the context or its previous siblings.
        if (QUOTES.any { context.text.startsWith(it) }) {
            quoteType = QUOTES.first { context.text.startsWith(it) }
        }

        while (current != null && attempts < 50) {
            attempts++
            val currentType = current.node?.elementType?.toString() ?: ""
            val text = current.text

            if (!foundColon && (currentType.contains("string", ignoreCase = true) || currentType.contains("literal", ignoreCase = true))) {
                if (quoteType.isEmpty() && QUOTES.any { text.startsWith(it) }) {
                    quoteType = QUOTES.first { text.startsWith(it) }
                }

                // If a previous string token is NOT just empty quotes, then IT is the first content-bearing token.
                // We must return here so we don't start injection on the wrong token.
                var contentStart = 0
                if (QUOTES.any { text.startsWith(it) }) contentStart = QUOTES.first { text.startsWith(it) }.length
                var contentEnd = text.length
                if (contentEnd > contentStart && QUOTES.any { text.endsWith(it) }) contentEnd -= QUOTES.first { text.endsWith(it) }.length

                if (contentEnd > contentStart) {
                    return // Found a previous token with actual content
                }
            }

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

            // If we couldn't determine quote type, abort
            if (quoteType.isEmpty() && QUOTES.any { context.text.startsWith(it) }) {
                quoteType = QUOTES.first { context.text.startsWith(it) }
            }
            if (quoteType.isEmpty()) return

            // Check if context actually has content. If it doesn't (it's just ending quotes), abort.
            var ctxStart = 0
            if (context.text.startsWith(quoteType)) ctxStart = quoteType.length
            var ctxEnd = context.text.length
            if (context.text.endsWith(quoteType) && context.text.length >= ctxStart + quoteType.length) ctxEnd -= quoteType.length
            if (ctxEnd <= ctxStart) return

            registrar.startInjecting(bashLanguage)

            var forwardNode: PsiElement? = context
            var addedAtLeastOnePlace = false

            while (forwardNode != null) {
                val forwardType = forwardNode.node?.elementType?.toString() ?: ""

                if (forwardType.contains("string", ignoreCase = true) || forwardType.contains("literal", ignoreCase = true)) {
                    val fText = forwardNode.text
                    var startOffset = 0
                    var endOffset = fText.length

                    if (fText.startsWith(quoteType)) {
                        startOffset = quoteType.length
                    }

                    if (fText.endsWith(quoteType) && fText.length >= startOffset + quoteType.length) {
                        endOffset -= quoteType.length
                    }

                    if (endOffset > startOffset && forwardNode is PsiLanguageInjectionHost) {
                        injectIntoText(registrar, forwardNode, fText, startOffset, endOffset)
                        addedAtLeastOnePlace = true
                    }

                    if (fText.endsWith(quoteType) && fText.length >= startOffset + quoteType.length) {
                        break
                    }
                }

                forwardNode = forwardNode.nextSibling
            }

            if (addedAtLeastOnePlace) {
                registrar.doneInjecting()
            }
        }
    }

    private fun injectIntoText(
        registrar: MultiHostRegistrar,
        host: PsiLanguageInjectionHost,
        text: String,
        startOffset: Int,
        endOffset: Int
    ) {
        var currentOffset = startOffset
        var searchIndex = startOffset

        while (searchIndex < endOffset) {
            val char = text[searchIndex]

            if (searchIndex > startOffset && text[searchIndex - 1] == '\\') {
                searchIndex++
                continue
            }

            if (char == '$' || char == '!') {
                val varStartIndex = searchIndex
                var varEndIndex = -1

                if (searchIndex + 1 < endOffset && text[searchIndex + 1] == '{') {
                    for (i in varStartIndex + 2 until endOffset) {
                        if (text[i] == '}') {
                            varEndIndex = i + 1
                            break
                        }
                    }
                } else {
                    var i = varStartIndex + 1
                    if (i < endOffset && (text[i].isLetter() || text[i] == '_')) {
                        i++
                        while (i < endOffset && (text[i].isLetterOrDigit() || text[i] == '_')) {
                            i++
                        }
                        varEndIndex = i
                    }
                }

                if (varEndIndex != -1) {
                    if (varStartIndex > currentOffset) {
                        registrar.addPlace(null, null, host, TextRange(currentOffset, varStartIndex))
                    }
                    currentOffset = varEndIndex
                    searchIndex = varEndIndex - 1
                }
            }
            searchIndex++
        }

        if (currentOffset < endOffset) {
            registrar.addPlace(null, null, host, TextRange(currentOffset, endOffset))
        }
    }
}
