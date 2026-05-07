package com.austinv11.nextflow

import com.intellij.extapi.psi.PsiFileBase
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
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition

private val FILE = IFileElementType(NextflowLanguage.INSTANCE)

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
    override fun createElement(node: ASTNode): PsiElement =
        throw UnsupportedOperationException("Nextflow uses a flat PSI tree with no composite elements")
    override fun createFile(viewProvider: FileViewProvider): PsiFile = NextflowPsiFile(viewProvider)
}

private class NextflowFlatParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val marker = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        marker.done(root)
        return builder.treeBuilt
    }
}

class NextflowPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, NextflowLanguage.INSTANCE) {
    override fun getFileType(): FileType = NextflowFileType.INSTANCE
}
