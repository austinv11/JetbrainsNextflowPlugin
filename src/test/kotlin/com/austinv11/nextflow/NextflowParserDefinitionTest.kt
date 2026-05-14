package com.austinv11.nextflow

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.lang.ParserDefinition
import com.intellij.openapi.project.Project
import com.intellij.lexer.Lexer
import com.intellij.lang.PsiParser
import java.lang.reflect.Field
import kotlin.reflect.KProperty1
import kotlin.reflect.jvm.javaField

class NextflowParserDefinitionTest : BasePlatformTestCase() {

    fun testParserDefinitionFallbackWhenGroovyMissing() {
        val parserDef = NextflowParserDefinition()

        // Since isGroovyAvailable is a private val initialized by lazy, we can use reflection to set the initialized state.
        val delegateField = NextflowParserDefinition::class.java.getDeclaredField("isGroovyAvailable\$delegate")
        delegateField.isAccessible = true

        // Replace the Lazy instance with our own returning false
        delegateField.set(parserDef, lazyOf(false))

        val lexer = parserDef.createLexer(project)
        assertEquals("FallbackLexer", lexer.javaClass.simpleName)

        val parser = parserDef.createParser(project)
        assertEquals("FallbackParser", parser.javaClass.simpleName)
    }

    fun testParserDefinitionWhenGroovyAvailable() {
        val parserDef = NextflowParserDefinition()

        val delegateField = NextflowParserDefinition::class.java.getDeclaredField("isGroovyAvailable\$delegate")
        delegateField.isAccessible = true
        delegateField.set(parserDef, lazyOf(true))

        val lexer = parserDef.createLexer(project)
        assertNotSame("FallbackLexer", lexer.javaClass.simpleName)

        val parser = parserDef.createParser(project)
        assertEquals("NextflowFlatParser", parser.javaClass.simpleName)
    }
}
