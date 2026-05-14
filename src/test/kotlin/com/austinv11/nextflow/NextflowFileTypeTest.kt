package com.austinv11.nextflow

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.application.ApplicationManager

class NextflowFileTypeTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ApplicationManager.getApplication().invokeAndWait {
            ApplicationManager.getApplication().runWriteAction {
                FileTypeManager.getInstance().associateExtension(NextflowFileType.INSTANCE, "nf")
                FileTypeManager.getInstance().associateExtension(NextflowFileType.INSTANCE, "nf.test")
            }
        }
    }

    override fun tearDown() {
        ApplicationManager.getApplication().invokeAndWait {
            ApplicationManager.getApplication().runWriteAction {
                FileTypeManager.getInstance().removeAssociatedExtension(NextflowFileType.INSTANCE, "nf")
                FileTypeManager.getInstance().removeAssociatedExtension(NextflowFileType.INSTANCE, "nf.test")
            }
        }
        super.tearDown()
    }

    fun testFileTypeRegistration() {
        val fileTypeManager = FileTypeManager.getInstance()

        val nfFileType = fileTypeManager.getFileTypeByExtension("nf")
        assertEquals(NextflowFileType.INSTANCE, nfFileType)
        assertEquals("Nextflow", nfFileType.name)
        assertEquals("nf", nfFileType.defaultExtension)

        assertEquals("Nextflow", NextflowLanguage.INSTANCE.id)
    }
}
