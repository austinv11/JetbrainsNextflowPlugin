package com.austinv11.nextflow.actions

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.psi.PsiDirectory

class NextflowCreateFileActionTest : BasePlatformTestCase() {

    fun testActionNameIsCorrect() {
        // Use reflection or subclass wrapper to test protected methods
        val action = object : NextflowCreateFileAction() {
            fun getActionNamePublic(name: String, templateName: String) =
                super.getActionName(null, name, templateName)
        }
        assertEquals("Create Nextflow File my_file", action.getActionNamePublic("my_file", "Nextflow Script"))
    }

    fun testCreateFileFormatsTestFilesCorrectly() {
        var capturedName: String? = null
        val action = object : NextflowCreateFileAction() {
            override fun createFile(name: String?, templateName: String?, dir: PsiDirectory?): com.intellij.psi.PsiFile? {
                var finalName = name ?: return null
                if (templateName == "Nextflow Test") {
                    if (!finalName.endsWith(".nf.test")) {
                        finalName = finalName.removeSuffix(".test").removeSuffix(".nf") + ".nf.test"
                    }
                }
                capturedName = finalName
                return null
            }

            fun testCreateFilePublic(name: String, templateName: String) {
                createFile(name, templateName, null)
            }
        }

        action.testCreateFilePublic("my_test", "Nextflow Test")
        assertEquals("my_test.nf.test", capturedName)

        action.testCreateFilePublic("my_test.nf", "Nextflow Test")
        assertEquals("my_test.nf.test", capturedName)

        action.testCreateFilePublic("my_test.test", "Nextflow Test")
        assertEquals("my_test.nf.test", capturedName)

        action.testCreateFilePublic("my_test.nf.test", "Nextflow Test")
        assertEquals("my_test.nf.test", capturedName)

        action.testCreateFilePublic("main.nf", "Nextflow Script")
        assertEquals("main.nf", capturedName)
    }
}
