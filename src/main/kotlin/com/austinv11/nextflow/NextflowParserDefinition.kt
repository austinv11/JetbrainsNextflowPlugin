package com.austinv11.nextflow

import com.austinv11.nextflow.util.NextflowEnvironmentUtils
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

private val FILE = IFileElementType(NextflowLanguage.INSTANCE)
val NEXTFLOW_STRING = IElementType("NEXTFLOW_STRING", NextflowLanguage.INSTANCE)
val NEXTFLOW_TEXT = IElementType("NEXTFLOW_TEXT", NextflowLanguage.INSTANCE)

class NextflowParserDefinition : ParserDefinition {

    private val groovyParser: ParserDefinition? by lazy {
        if (NextflowEnvironmentUtils.isGroovyAvailable) {
            org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition()
        } else null
    }

    override fun createLexer(project: Project?): Lexer =
        if (NextflowEnvironmentUtils.isGroovyAvailable) groovyParser!!.createLexer(project) else FallbackLexer()

    override fun getFileNodeType(): IFileElementType = FILE
    override fun getWhitespaceTokens(): TokenSet = groovyParser?.whitespaceTokens ?: TokenSet.EMPTY
    override fun getCommentTokens(): TokenSet = groovyParser?.commentTokens ?: TokenSet.EMPTY
    override fun getStringLiteralElements(): TokenSet = groovyParser?.stringLiteralElements ?: TokenSet.EMPTY
    override fun createParser(project: Project?): PsiParser = if (NextflowEnvironmentUtils.isGroovyAvailable) NextflowFlatParser() else FallbackParser()

    override fun createElement(node: ASTNode): PsiElement {
        if (node.elementType == NEXTFLOW_STRING) {
            return NextflowStringLiteral(node)
        }
        if (!NextflowEnvironmentUtils.isGroovyAvailable && node.elementType == NEXTFLOW_TEXT) {
            return ASTWrapperPsiElement(node)
        }
        throw UnsupportedOperationException("Nextflow uses a flat PSI tree with no composite elements except NEXTFLOW_STRING")
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile = NextflowPsiFile(viewProvider)
}

private class NextflowFlatParser : PsiParser {
    private val groovyParser by lazy { org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition() }

    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val marker = builder.mark()
        val stringTokens = groovyParser.stringLiteralElements

        while (!builder.eof()) {
            val tokenType = builder.tokenType
            if (tokenType != null && stringTokens.contains(tokenType)) {
                val stringMarker = builder.mark()
                builder.advanceLexer()
                stringMarker.done(NEXTFLOW_STRING)
            } else {
                builder.advanceLexer()
            }
        }
        marker.done(root)
        return builder.treeBuilt
    }
}

internal class FallbackLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var start = 0
    private var end = 0
    private var position = 0

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.start = startOffset
        this.end = endOffset
        this.position = startOffset
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? {
        if (position >= end) return null

        val c = buffer[position]
        if (c.isWhitespace()) {
            var i = position
            while (i < end && buffer[i].isWhitespace()) i++
            return com.intellij.psi.TokenType.WHITE_SPACE
        }

        if (c == '\'' || c == '"') {
            return NEXTFLOW_STRING
        }

        return NEXTFLOW_TEXT
    }

    override fun getTokenStart(): Int = position

    override fun getTokenEnd(): Int {
        if (position >= end) return position
        val c = buffer[position]
        if (c.isWhitespace()) {
            var i = position
            while (i < end && buffer[i].isWhitespace()) i++
            return i
        }

        if (c == '\'' || c == '"') {
            val quote = c
            var i = position + 1
            var isTriple = false
            if (i + 1 < end && buffer[i] == quote && buffer[i+1] == quote) {
                isTriple = true
                i += 2
            }
            while (i < end) {
                if (buffer[i] == '\\') {
                    i += 2
                    continue
                }
                if (isTriple) {
                    if (buffer[i] == quote && i + 2 < end && buffer[i+1] == quote && buffer[i+2] == quote) {
                        return i + 3
                    }
                } else if (buffer[i] == quote) {
                    return i + 1
                }
                i++
            }
            return end
        }

        var i = position + 1
        while (i < end && !buffer[i].isWhitespace() && buffer[i] != '\'' && buffer[i] != '"') {
            i++
        }
        return i
    }

    override fun advance() { position = getTokenEnd() }
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = end
}

private class FallbackParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val marker = builder.mark()
        while (!builder.eof()) {
            builder.advanceLexer()
        }
        marker.done(root)
        return builder.treeBuilt
    }
}

class NextflowPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, NextflowLanguage.INSTANCE) {
    override fun getFileType(): FileType = NextflowFileType.INSTANCE
}

class NextflowStringLiteral(node: ASTNode) : ASTWrapperPsiElement(node), PsiLanguageInjectionHost {
    override fun isValidHost(): Boolean = true
    override fun updateText(text: String): PsiLanguageInjectionHost = this
    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> = LiteralTextEscaper.createSimple(this)
}
