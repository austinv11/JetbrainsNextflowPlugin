package com.austinv11.nextflow

import com.austinv11.nextflow.util.NextflowEnvironmentUtils
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.textmate.TextMateService

class NextflowFileTypeTest : BasePlatformTestCase() {

    fun testScriptsAreRecognizedWithoutManualAssociations() {
        for (name in listOf("main.nf", "modules/example.nf", "main.nf.test")) {
            val file = myFixture.addFileToProject(name, "// Nextflow script").virtualFile
            assertNextflowFile(file)
            assertNotNull(TextMateService.getInstance().getLanguageDescriptorByFileName(file.name))
        }
    }

    fun testPipelineConfigsAreRecognizedWithoutManualAssociations() {
        val config = myFixture.addFileToProject("pipeline/nextflow.config", "process.cpus = 2").virtualFile
        val included = myFixture.addFileToProject("pipeline/conf/base.config", "process.memory = '2 GB'").virtualFile
        assertNextflowFile(config)
        assertNextflowFile(included)
        assertNotNull(TextMateService.getInstance().getLanguageDescriptorByFileName(included.name))
    }

    private fun assertNextflowFile(file: VirtualFile) {
        // Exercise real detection: assigning .nf to Nextflow here would hide regressions.
        val fileType = file.fileType
        if (NextflowEnvironmentUtils.isGroovyAvailable) {
            assertSame(file.path, NextflowFileType.INSTANCE, fileType)
            assertEquals(file.path, NextflowLanguage.INSTANCE, psiManager.findFile(file)!!.language)
        } else {
            assertTrue("Expected TextMate fallback for ${file.path}: $fileType", fileType is LanguageFileType)
            assertEquals(file.path, "TEXTMATE", (fileType as LanguageFileType).language.id.uppercase())
        }
        assertSame(file.path, NextflowIcons.FILE, NextflowFileIconProvider().getIcon(file, 0, project))
    }
}
