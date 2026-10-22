package com.austinv11.nextflow

import com.intellij.ide.FileIconProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NextflowFileTypeOverriderTest : BasePlatformTestCase() {

    fun testNextflowConfigIsOverridden() {
        val file = myFixture.configureByText("nextflow.config", "params.foo = 'bar'").virtualFile
        val overrider = NextflowFileTypeOverrider()
        val fileType = overrider.getOverriddenFileType(file)
        assertEquals(NextflowFileType.INSTANCE, fileType)
    }

    fun testNfTestIsOverridden() {
        val file = myFixture.configureByText("test.nf.test", "assert true").virtualFile
        val overrider = NextflowFileTypeOverrider()
        val fileType = overrider.getOverriddenFileType(file)
        assertEquals(NextflowFileType.INSTANCE, fileType)
    }

    fun testOtherFilesAreNotOverridden() {
        val overrider = NextflowFileTypeOverrider()
        val file = myFixture.configureByText("test.txt", "hello").virtualFile
        val fileType = overrider.getOverriddenFileType(file)
        assertNull(fileType)
    }

    fun testIncludedConfigurationUsesNextflowFileTypeParserAndIcon() {
        myFixture.addFileToProject("pipeline/nextflow.config", "includeConfig 'conf/base.config'")
        val file = myFixture.addFileToProject("pipeline/conf/base.config", "process.cpus = 2").virtualFile
        assertSame(NextflowFileType.INSTANCE, file.fileType)
        assertEquals(NextflowLanguage.INSTANCE, psiManager.findFile(file)!!.language)
        assertSame(NextflowIcons.FILE, file.fileType.icon)
        assertSame(NextflowIcons.FILE, NextflowFileIconProvider().getIcon(file, 0, project))
    }

    fun testUnrelatedAndNfTestConfigsKeepTheirFileTypesAndIcons() {
        myFixture.addFileToProject("pipeline/nextflow.config", "")
        for (path in listOf("application.config", "other/app.config", "pipeline/nf-test.config")) {
            val file = myFixture.addFileToProject(path, "").virtualFile
            assertNull(path, NextflowFileTypeOverrider().getOverriddenFileType(file))
            assertNull(path, NextflowFileIconProvider().getIcon(file, 0, project))
        }
    }

    fun testIconProviderIsRegisteredIndependentlyOfGroovySupport() {
        assertTrue(FileIconProvider.EP_NAME.extensionList.any { it is NextflowFileIconProvider })
    }
}
