package com.austinv11.nextflow

import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.fileTypes.FileTypeManager

class NextflowFileTypeOverriderTest : BasePlatformTestCase() {

    fun testNextflowConfigIsOverridden() {
        val file = myFixture.configureByText("nextflow.config", "params.foo = 'bar'").virtualFile
        val fileType = FileTypeManager.getInstance().getFileTypeByFile(file)
        assertEquals(NextflowFileType.INSTANCE, fileType)
    }

    fun testNfTestIsOverridden() {
        val file = myFixture.configureByText("test.nf.test", "assert true").virtualFile
        val fileType = FileTypeManager.getInstance().getFileTypeByFile(file)
        assertEquals(NextflowFileType.INSTANCE, fileType)
    }

    fun testOtherFilesAreNotOverridden() {
        val overrider = NextflowFileTypeOverrider()
        val file = myFixture.configureByText("test.txt", "hello").virtualFile
        val fileType = overrider.getOverriddenFileType(file)
        assertNull(fileType)
    }
}
