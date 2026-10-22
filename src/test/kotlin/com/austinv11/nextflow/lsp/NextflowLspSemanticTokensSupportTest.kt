package com.austinv11.nextflow.lsp

import com.austinv11.nextflow.NextflowLanguage
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NextflowLspSemanticTokensSupportTest : BasePlatformTestCase() {
    fun testSemanticStringsDoNotOverrideEmbeddedSyntaxColors() {
        for (modifiers in listOf(emptyList(), listOf("declaration"), listOf("readonly"))) {
            assertNull("String coloring must remain with the syntax highlighter", support().getTextAttributesKey("string", modifiers))
        }
    }

    fun testOtherSemanticColorsRetainPlatformMappings() {
        val platform = LspSemanticTokensSupport()
        val actual = support()
        for (type in platform.tokenTypes.filter { it != "string" }) {
            for (modifiers in listOf(emptyList(), listOf("static"), listOf("defaultLibrary"))) {
                assertEquals("Preserve the platform mapping for $type with $modifiers",
                    platform.getTextAttributesKey(type, modifiers), actual.getTextAttributesKey(type, modifiers))
            }
        }
    }

    fun testInitializationPreservesPlatformSemanticTokenCapabilities() {
        val platform = object : ProjectWideLspServerDescriptor(project, "Platform defaults") {
            override val lspCustomization = LspCustomization()
            override fun isSupportedFile(file: VirtualFile) = false
            override fun createCommandLine() = GeneralCommandLine("unused")
        }.createInitializeParams().capabilities.textDocument.semanticTokens
        val actual = NextflowLspServerDescriptor(project).createInitializeParams().capabilities.textDocument.semanticTokens

        assertNotNull(platform)
        assertNotNull(actual)
        assertNotEmpty(actual.tokenTypes)
        assertNotEmpty(actual.tokenModifiers)
        assertEquals(platform.tokenTypes, actual.tokenTypes)
        assertEquals(platform.tokenModifiers, actual.tokenModifiers)
        assertEquals(platform.requests, actual.requests)
        assertEquals(platform.formats, actual.formats)
        assertEquals(platform.overlappingTokenSupport, actual.overlappingTokenSupport)
        assertEquals(platform.multilineTokenSupport, actual.multilineTokenSupport)
        assertEquals(true, actual.augmentsSyntaxTokens)
    }

    fun testNextflowParserFilesReceiveSemanticTokens() {
        val file = PsiFileFactory.getInstance(project).createFileFromText("main.nf", NextflowLanguage.INSTANCE, "channel.of('hello').view()")
        assertNotNull(file)
        assertTrue(support().shouldAskServerForSemanticTokens(file!!))
    }

    fun testSupportedFilesReceiveTokensWithPlainTextFallback() {
        for (name in listOf("main.nf", "nextflow.config")) {
            val file = PsiFileFactory.getInstance(project).createFileFromText(name, PlainTextFileType.INSTANCE, "")
            assertTrue("Semantic tokens should be requested for $name", support().shouldAskServerForSemanticTokens(file))
        }
    }

    fun testUnrelatedFilesDoNotReceiveNextflowTokens() {
        for (name in listOf("README.txt", "build.gradle", "other.config", "main.nf.test")) {
            val file = PsiFileFactory.getInstance(project).createFileFromText(name, PlainTextFileType.INSTANCE, "")
            assertFalse("The Nextflow server does not support $name", support().shouldAskServerForSemanticTokens(file))
        }
    }

    private fun support(): LspSemanticTokensSupport =
        NextflowLspServerDescriptor(project).lspCustomization.semanticTokensCustomizer as LspSemanticTokensSupport
}
