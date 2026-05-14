package com.austinv11.nextflow

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.psi.TokenType
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NextflowSyntaxHighlighterTest : BasePlatformTestCase() {
    fun testKeywordsAreHighlighted() {
        val highlighter = NextflowSyntaxHighlighter(project)
        val lexer = highlighter.highlightingLexer

        lexer.start("process foo { }")

        // "process" should be a keyword
        assertEquals("NF_KEYWORD", lexer.tokenType.toString())

        val highlights = highlighter.getTokenHighlights(lexer.tokenType)
        assertTrue(highlights.contains(DefaultLanguageHighlighterColors.KEYWORD))

        lexer.advance()
        assertEquals(TokenType.WHITE_SPACE, lexer.tokenType)

        lexer.advance()
        val nextType = lexer.tokenType.toString()
        assertTrue(nextType == "mIDENT" || nextType == "identifier")
        assertEquals("foo", lexer.tokenText)
    }
}
