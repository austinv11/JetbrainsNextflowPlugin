package com.austinv11.nextflow.lsp

import com.austinv11.nextflow.util.NextflowFileUtils
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NextflowConfigSupportTest : BasePlatformTestCase() {
    fun testIncludedAndProfileConfigsReceiveLanguageServerSupport() {
        myFixture.addFileToProject("pipeline/nextflow.config", "includeConfig 'conf/base.config'")
        val descriptor = NextflowLspServerDescriptor(project)
        val semantic = descriptor.lspCustomization.semanticTokensCustomizer as LspSemanticTokensSupport
        for (path in listOf("pipeline/conf/base.config", "pipeline/conf/profiles/slurm.config")) {
            val file = myFixture.addFileToProject(path, "process.cpus = 2")
            assertTrue(path, NextflowFileUtils.isNextflowConfig(project, file.virtualFile))
            assertTrue(descriptor.isSupportedFile(file.virtualFile))
            assertEquals("nextflow-config", descriptor.getLanguageId(file.virtualFile))
            assertTrue(semantic.shouldAskServerForSemanticTokens(file))
        }
    }

    fun testSiblingProjectsAndNfTestConfigurationAreNotRecognized() {
        myFixture.addFileToProject("pipeline/nextflow.config", "")
        val descriptor = NextflowLspServerDescriptor(project)
        val semantic = descriptor.lspCustomization.semanticTokensCustomizer as LspSemanticTokensSupport
        for (path in listOf("application.config", "other/app.config", "pipeline/nf-test.config", "pipeline/conf/nf-test.config")) {
            val file = myFixture.addFileToProject(path, "")
            assertFalse(path, NextflowFileUtils.isNextflowConfig(project, file.virtualFile))
            assertFalse(descriptor.isSupportedFile(file.virtualFile))
            assertFalse(semantic.shouldAskServerForSemanticTokens(file))
        }
    }

    fun testRecognitionUpdatesWhenPipelineMarkerIsAddedAndRemoved() {
        val file = myFixture.addFileToProject("pipeline/conf/base.config", "")
        val descriptor = NextflowLspServerDescriptor(project)
        assertFalse(descriptor.isSupportedFile(file.virtualFile))
        val marker = myFixture.addFileToProject("pipeline/nextflow.config", "").virtualFile
        assertTrue(descriptor.isSupportedFile(file.virtualFile))
        WriteCommandAction.runWriteCommandAction(project) { marker.delete(this) }
        assertFalse(descriptor.isSupportedFile(file.virtualFile))
    }

    fun testMarkerOutsideTheContentRootDoesNotClaimAnotherModule() {
        myFixture.addFileToProject("nextflow.config", "")
        val file = myFixture.addFileToProject("separate/conf/base.config", "")
        PsiTestUtil.addContentRoot(module, file.virtualFile.parent.parent)
        assertFalse(NextflowFileUtils.isNextflowConfig(project, file.virtualFile))
    }

    fun testDirectoryNamedNextflowConfigIsNotAPipelineMarker() {
        myFixture.tempDirFixture.findOrCreateDir("pipeline/nextflow.config")
        val file = myFixture.addFileToProject("pipeline/base.config", "")
        assertFalse(NextflowFileUtils.isNextflowConfig(project, file.virtualFile))
    }

    fun testLegacyRootConfigAndScriptRecognitionArePreserved() {
        val descriptor = NextflowLspServerDescriptor(project)
        for (name in listOf("nextflow.config", "main.nf")) {
            assertTrue(descriptor.isSupportedFile(myFixture.addFileToProject(name, "").virtualFile))
        }
        assertFalse(descriptor.isSupportedFile(myFixture.addFileToProject("main.nf.test", "").virtualFile))
        assertFalse(descriptor.isSupportedFile(myFixture.tempDirFixture.findOrCreateDir("folder.config")))
    }
}
