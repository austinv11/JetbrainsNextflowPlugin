package com.austinv11.nextflow.lsp

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files

class NextflowLspFileUriTest : BasePlatformTestCase() {
    fun testWindowsDriveMatchesServerFilesystemUri() {
        assertEquals("file:///c:/work/main.nf", normalizeNextflowFileUri("file:///c%3A/work/main.nf"))
        assertEquals("file:///D:/work/main.nf", normalizeNextflowFileUri("file:///D%3a/work/main.nf"))
    }

    fun testEscapedFilenameCharactersArePreserved() {
        assertEquals(
            "file:///c:/my%20project/a%23b%253A.nf",
            normalizeNextflowFileUri("file:///c%3A/my%20project/a%23b%253A.nf")
        )
    }

    fun testOtherUrisRemainUnchanged() {
        for (uri in listOf("file:///home/user/main.nf", "file://server/share/main.nf", "file:///c:/work/main.nf", "untitled:///c%3A/main.nf")) {
            assertEquals(uri, normalizeNextflowFileUri(uri))
        }
    }

    fun testDescriptorFileUriRoundTripsWithEscapedFilename() {
        val path = Files.createTempFile("nextflow uri #", ".txt")
        try {
            val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)!!
            val descriptor = NextflowLspServerDescriptor(project)
            val uri = descriptor.getFileUri(file)
            assertFalse("The drive separator must match the server's filesystem scan", uri.contains(Regex("^file:///[a-zA-Z]%3[aA]/")))
            assertTrue(uri.contains("%20"))
            assertTrue(uri.contains("%23"))
            assertEquals(file, descriptor.findFileByUri(uri))
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
