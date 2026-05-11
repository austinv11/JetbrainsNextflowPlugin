package com.austinv11.nextflow

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
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
import org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition

private val FILE = IFileElementType(NextflowLanguage.INSTANCE)
val NEXTFLOW_STRING = IElementType("NEXTFLOW_STRING", NextflowLanguage.INSTANCE)

// Uses Groovy's lexer so that GroovyBraceMatcher and GroovyCommenter (registered in
// plugin.xml) see the right token types. The parser is intentionally flat — all tokens
// are leaf children of the root — so no composite Groovy PSI elements are created.
// Without composite Groovy elements, Groovy annotators and intentions cannot fire on
// .nf files, and TextMate retains sole ownership of syntax highlighting.
class NextflowParserDefinition : ParserDefinition {
    private val groovy = GroovyParserDefinition()

    override fun createLexer(project: Project?): Lexer = groovy.createLexer(project)
    override fun getFileNodeType(): IFileElementType = FILE
    override fun getWhitespaceTokens(): TokenSet = groovy.whitespaceTokens
    override fun getCommentTokens(): TokenSet = groovy.commentTokens
    override fun getStringLiteralElements(): TokenSet = groovy.stringLiteralElements
    override fun createParser(project: Project?): PsiParser = NextflowFlatParser()
    override fun createElement(node: ASTNode): PsiElement {
        if (node.elementType == NEXTFLOW_STRING) {
            return NextflowStringLiteral(node)
        }
        throw UnsupportedOperationException("Nextflow uses a flat PSI tree with no composite elements except NEXTFLOW_STRING")
    }
    override fun createFile(viewProvider: FileViewProvider): PsiFile = NextflowPsiFile(viewProvider)
}

private class NextflowFlatParser : PsiParser {
    private val groovy = GroovyParserDefinition()

    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val marker = builder.mark()
        val stringTokens = groovy.stringLiteralElements

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

class NextflowPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, NextflowLanguage.INSTANCE) {
    override fun getFileType(): FileType = NextflowFileType.INSTANCE
}

class NextflowStringLiteral(node: ASTNode) : ASTWrapperPsiElement(node), PsiLanguageInjectionHost {
    override fun isValidHost(): Boolean = true
    override fun updateText(text: String): PsiLanguageInjectionHost = this // Minimal implementation for flat PSI
    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> = LiteralTextEscaper.createSimple(this)
}
