package com.austinv11.nextflow

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.plugins.textmate.TextMateService
import java.nio.file.Files
import com.intellij.openapi.application.ApplicationManager

class NextflowTextMateBundleProviderTest : BasePlatformTestCase() {

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

    fun testBundleIsProvided() {
        val provider = NextflowTextMateBundleProvider()
        val bundles = provider.getBundles()

        assertTrue("TextMateBundleProvider should return at least one bundle", bundles.isNotEmpty())
        val bundle = bundles.first()

        assertEquals("Nextflow", bundle.name)
        assertTrue(Files.exists(bundle.path))
        assertTrue(Files.exists(bundle.path.resolve("package.json")))
    }

    fun testTextMateServiceIsAvailable() {
        // Assert that TextMateService exists and Nextflow filetype maps correctly
        val service = TextMateService.getInstance()
        assertNotNull("TextMateService should be available", service)

        val fileType = FileTypeManager.getInstance().getFileTypeByExtension("nf")
        assertEquals("Nextflow", fileType.name)

        // Ensure TextMate loads without exception, which inherently uses our provider since it's registered in plugin.xml
        service.reloadEnabledBundles()
    }
}
