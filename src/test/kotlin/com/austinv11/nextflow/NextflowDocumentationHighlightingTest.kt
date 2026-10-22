package com.austinv11.nextflow

import com.intellij.lang.documentation.QuickDocHighlightingHelper
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NextflowDocumentationHighlightingTest : BasePlatformTestCase() {
    fun testFileTypeRegistrationLeavesExtensionAssociationsDynamic() {
        val manager = FileTypeManager.getInstance()
        assertSame(NextflowFileType.INSTANCE, manager.findFileTypeByName("Nextflow"))
        assertEmpty(manager.getAssociations(NextflowFileType.INSTANCE))
    }

    fun testDocumentationSnippetCanCreateNextflowPsiFileWithoutExtensionAssociations() {
        val code = "channel.of('hello').view()"
        val file = PsiFileFactory.getInstance(project).createFileFromText(NextflowLanguage.INSTANCE, code)
        assertNotNull("Nextflow documentation snippets must have a PSI file", file)
        assertEquals(code, file!!.text)
        assertEquals(NextflowLanguage.INSTANCE, file.language)
    }

    fun testHoverSignatureAndCodeBlockCanBeHighlighted() {
        val signature = QuickDocHighlightingHelper.getStyledSignatureFragment(project, NextflowLanguage.INSTANCE, "channel.of(value)")
        assertTrue(signature.contains("channel"))
        val block = QuickDocHighlightingHelper.getStyledCodeBlock(project, NextflowLanguage.INSTANCE, "workflow { channel.of('hello').view() }")
        assertTrue(block.contains("workflow"))
    }
}
