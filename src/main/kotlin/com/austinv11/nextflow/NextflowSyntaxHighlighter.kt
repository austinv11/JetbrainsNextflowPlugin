package com.austinv11.nextflow

import com.austinv11.nextflow.util.GroovyFacade
import com.austinv11.nextflow.util.NextflowEnvironmentUtils
import com.intellij.ide.environment.impl.EnvironmentUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerPosition
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType

private val NF_KEYWORD = IElementType("NF_KEYWORD", NextflowLanguage.INSTANCE)

// Nextflow-specific identifiers that should render as keywords.
// Groovy's lexer produces these as mIDENT (from org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes); we intercept and remap them.
private val NF_KEYWORDS = setOf(
    "process", "workflow", "channel", "params", "nextflow",
    "input", "output", "script", "shell", "exec", "when", "stub",
    "emit", "take", "main", "include", "Channel",
)


class NextflowSyntaxHighlighter(private val project: Project?) : SyntaxHighlighterBase() {
    private val groovy: SyntaxHighlighter? by lazy {
        if (NextflowEnvironmentUtils.isGroovyAvailable) GroovyFacade.createGroovySyntaxHighlighter(project) else null
    }

    override fun getHighlightingLexer(): Lexer {
        return if (NextflowEnvironmentUtils.isGroovyAvailable) createGroovyLexer()
        else org.jetbrains.plugins.textmate.psi.TextMateParserDefinition().createLexer(project)
    }

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
        if (tokenType === NF_KEYWORD) pack(DefaultLanguageHighlighterColors.KEYWORD)
        else if (NextflowEnvironmentUtils.isGroovyAvailable) groovy?.getTokenHighlights(tokenType) ?: emptyArray()
        else emptyArray()
    private fun createGroovyLexer(): Lexer = NextflowKeywordLexer(GroovyFacade.createGroovyLexer(project))

    // Wraps Groovy's lexer and remaps known Nextflow identifier tokens to NF_KEYWORD.
    // All other methods delegate to the base so that token offsets and lexer state
    // (needed by GroovyBraceMatcher and GroovyCommenter) remain correct.
    private class NextflowKeywordLexer(private val base: Lexer) : Lexer() {
        override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) =
            base.start(buffer, startOffset, endOffset, initialState)

        override fun getState(): Int = base.state
        override fun getTokenStart(): Int = base.tokenStart
        override fun getTokenEnd(): Int = base.tokenEnd
        override fun advance() = base.advance()
        override fun getCurrentPosition(): LexerPosition = base.currentPosition
        override fun restore(position: LexerPosition) = base.restore(position)
        override fun getBufferSequence(): CharSequence = base.bufferSequence
        override fun getBufferEnd(): Int = base.bufferEnd

        override fun getTokenType(): IElementType? {
            val type = base.tokenType ?: return null
            // We fully qualify to avoid classloader issues if Groovy isn't available
            if (GroovyFacade.isGroovyIdent(type)) {
                val text = base.bufferSequence.subSequence(base.tokenStart, base.tokenEnd).toString()
                if (text in NF_KEYWORDS) return NF_KEYWORD
            }
            return type
        }
    }
}

class NextflowSyntaxHighlighterFactory : SyntaxHighlighterFactory() {

    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter {
        if (NextflowEnvironmentUtils.isGroovyAvailable) {
            return NextflowSyntaxHighlighter(project)
        }

        // Because of PluginClassLoader restrictions, directly reflecting on TextMateSyntaxHighlighterFactory fails.
        // Returning null from a Factory crashes the IDE editor subsystem.
        // We must provide an empty syntax highlighter so the IDE allows Editor creation to proceed,
        // and allow TextMate service to do string-based fallback via background annotators or TextMateEditorHighlighterProvider
        // using the file extension mapping instead of overriding the language syntax.
        return object : SyntaxHighlighterBase() {
            override fun getHighlightingLexer(): Lexer = org.jetbrains.plugins.textmate.psi.TextMateParserDefinition().createLexer(project)
            override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> = emptyArray()
        }
    }
}