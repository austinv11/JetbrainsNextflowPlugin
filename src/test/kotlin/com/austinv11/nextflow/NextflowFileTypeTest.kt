package com.austinv11.nextflow

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NextflowFileTypeTest : BasePlatformTestCase() {
    fun testFileTypeRegistration() {
        val fileTypeManager = FileTypeManager.getInstance()

        val nfFileType = fileTypeManager.getFileTypeByExtension("nf")
        assertEquals(NextflowFileType.INSTANCE, nfFileType)
        assertEquals("Nextflow", nfFileType.name)
        assertEquals("nf", nfFileType.defaultExtension)

        val configFileType = fileTypeManager.getFileTypeByExtension("config")
        // Note: nextflow.config isn't mapped just by .config extension alone in filetypes
        // But let's verify NextflowLanguage is correct
        assertEquals("Nextflow", NextflowLanguage.INSTANCE.id)
    }
}
