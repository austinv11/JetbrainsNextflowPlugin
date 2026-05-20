package com.austinv11.nextflow.util

import com.intellij.lang.ParserDefinition
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet

/**
 * A facade to isolate Groovy plugin dependencies.
 * This class should ONLY be accessed when [NextflowEnvironmentUtils.isGroovyAvailable] is true.
 * This prevents NoClassDefFoundError at plugin startup in environments (like PyCharm) where the Groovy plugin is absent.
 */
object GroovyFacade {
    fun createGroovyParserDefinition(): ParserDefinition {
        return org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition()
    }

    fun getWhitespaceTokens(): TokenSet {
        return org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition().whitespaceTokens
    }

    fun getCommentTokens(): TokenSet {
        return org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition().commentTokens
    }

    fun getStringLiteralElements(): TokenSet {
        return org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition().stringLiteralElements
    }

    fun createGroovySyntaxHighlighter(project: Project?): SyntaxHighlighter? {
        return org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighterFactory().getSyntaxHighlighter(project, null)
    }

    fun createGroovyLexer(project: Project?): Lexer {
        return org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition().createLexer(project)
    }

    fun isGroovyIdent(type: IElementType): Boolean {
        return type == org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mIDENT
    }
}
