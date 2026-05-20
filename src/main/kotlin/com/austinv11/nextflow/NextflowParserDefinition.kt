package com.austinv11.nextflow

import com.austinv11.nextflow.util.GroovyFacade
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

class NextflowParserDefinition : org.jetbrains.plugins.textmate.psi.TextMateParserDefinition() {

    private val groovyParser: ParserDefinition? by lazy {
        if (NextflowEnvironmentUtils.isGroovyAvailable) {
            GroovyFacade.createGroovyParserDefinition()
        } else null
    }

    override fun createLexer(project: Project?): Lexer =
        if (NextflowEnvironmentUtils.isGroovyAvailable) groovyParser!!.createLexer(project) else super.createLexer(project)

    override fun getFileNodeType(): IFileElementType = FILE
    override fun getWhitespaceTokens(): TokenSet = if (NextflowEnvironmentUtils.isGroovyAvailable) GroovyFacade.getWhitespaceTokens() else TokenSet.EMPTY
    override fun getCommentTokens(): TokenSet = if (NextflowEnvironmentUtils.isGroovyAvailable) GroovyFacade.getCommentTokens() else TokenSet.EMPTY
    override fun getStringLiteralElements(): TokenSet = if (NextflowEnvironmentUtils.isGroovyAvailable) GroovyFacade.getStringLiteralElements() else TokenSet.EMPTY
    override fun createParser(project: Project?): PsiParser = if (NextflowEnvironmentUtils.isGroovyAvailable) NextflowFlatParser() else super.createParser(project)

    override fun createElement(node: ASTNode): PsiElement {
        if (node.elementType == NEXTFLOW_STRING) {
            return NextflowStringLiteral(node)
        }
        if (!NextflowEnvironmentUtils.isGroovyAvailable && node.elementType == NEXTFLOW_TEXT) {
            return ASTWrapperPsiElement(node)
        }
        return super.createElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile = NextflowPsiFile(viewProvider)
}

private class NextflowFlatParser : PsiParser {

    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val marker = builder.mark()
        val stringTokens = GroovyFacade.getStringLiteralElements()

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
    override fun updateText(text: String): PsiLanguageInjectionHost = this
    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> = LiteralTextEscaper.createSimple(this)
}