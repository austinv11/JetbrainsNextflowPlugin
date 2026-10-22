package com.austinv11.nextflow.lsp

import com.austinv11.nextflow.NextflowSettings
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl

class NextflowWorkspaceWatcherTest : BasePlatformTestCase() {
    private lateinit var originalState: NextflowSettings.State
    private val openFiles = mutableSetOf<VirtualFile>()
    private var restartRequested = false

    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    override fun setUp() {
        super.setUp()
        originalState = NextflowSettings.getInstance(project).state.copy()
        NextflowSettings.getInstance(project).loadState(NextflowSettings.State())
        PsiTestUtil.addContentRoot(module, myFixture.tempDirFixture.findOrCreateDir(""))
        val changes = NextflowWorkspaceChanges(project) { it in openFiles }
        project.messageBus.connect(testRootDisposable).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun before(events: List<VFileEvent>) {
                restartRequested = restartRequested || changes.requiresRestartBefore(events)
            }

            override fun after(events: List<VFileEvent>) {
                restartRequested = restartRequested || changes.requiresRestartAfter(events)
            }
        })
    }

    override fun tearDown() {
        try {
            NextflowSettings.getInstance(project).loadState(originalState)
        } finally {
            super.tearDown()
        }
    }

    fun testClosedScriptsConfigurationsAndSchemasTriggerRefresh() {
        for (name in listOf("module.nf", "nextflow.config", "conf/base.config", "nextflow_schema.json")) {
            val file = create(name)
            assertTrue(name, change { file.setBinaryContent("updated".toByteArray()) })
        }
    }

    fun testOpenLspDocumentsUseLiveUpdatesButOtherInputsStillRefresh() {
        val marker = create("nextflow.config")
        for (file in listOf(create("module.nf"), marker, create("conf/base.config"))) {
            openFiles.add(file)
            assertFalse(file.name, change { file.setBinaryContent("updated".toByteArray()) })
        }
        val schema = create("nextflow_schema.json")
        openFiles.add(schema)
        assertTrue(change { schema.setBinaryContent("updated".toByteArray()) })
    }

    fun testUnmarkedConfigurationFilesDoNotTriggerRestart() {
        val file = create("application.config")
        assertFalse(change { file.setBinaryContent("updated".toByteArray()) })
    }

    fun testUnrelatedFilesAndUnsupportedNfTestFilesAreIgnored() {
        for (name in listOf("README.md", "results.txt", "params.json", "main.nf.test", "nf-test.config")) {
            assertFalse(name, change { create(name) })
        }
    }

    fun testCreatingAndCopyingPipelineFilesTriggersRefresh() {
        assertTrue(change { create("new.nf") })
        val file = create("source.nf")
        val destination = myFixture.tempDirFixture.findOrCreateDir("modules")
        assertTrue(change { file.copy(this, destination, "copy.nf") })
    }

    fun testRenamingIntoAndOutOfSupportedExtensionsTriggersRefresh() {
        val file = create("module.nf")
        assertTrue(change { file.rename(this, "module.txt") })
        assertTrue(change { file.rename(this, "restored.nf") })
        assertTrue(change { file.rename(this, "renamed.nf") })
    }

    fun testDirectoryRenameMoveAndDeletionFindNestedPipelineFiles() {
        create("modules/nested/task.nf")
        val directory = myFixture.tempDirFixture.getFile("modules")!!
        val destination = myFixture.tempDirFixture.findOrCreateDir("other")
        assertTrue(change { directory.rename(this, "renamed") })
        assertTrue(change { directory.move(this, destination) })
        assertTrue(change { directory.delete(this) })
    }

    fun testExcludedPathsAreIgnoredAndMovesAcrossExclusionsAreDetected() {
        NextflowSettings.getInstance(project).state.filesExclude = ".git, .nf-test, work, generated"
        for (name in listOf("work/ab/task.nf", "generated/base.config", ".nf-test/nextflow_schema.json")) {
            assertFalse(name, change { create(name) })
        }
        val file = create("task.nf")
        val work = myFixture.tempDirFixture.getFile("work")!!
        val root = file.parent
        assertTrue(change { file.move(this, work) })
        assertTrue(change { file.move(this, root) })
    }

    fun testIdeExcludedRootsAndUnrelatedDirectoriesDoNotTriggerRefresh() {
        create("excluded/task.nf")
        val excluded = myFixture.tempDirFixture.getFile("excluded")!!
        PsiTestUtil.addExcludedRoot(module, excluded)
        assertFalse(change { excluded.delete(this) })
        create("assets/readme.txt")
        val assets = myFixture.tempDirFixture.getFile("assets")!!
        assertFalse(change { assets.rename(this, "images") })
        assertFalse(change { assets.delete(this) })
    }

    fun testChangesOutsideThisProjectsContentAreIgnored() {
        val file = create("outside/task.nf")
        // Removing this content root leaves the file in the same VFS but outside the project.
        PsiTestUtil.removeContentEntry(module, file.parent.parent)
        assertFalse(change { file.setBinaryContent("updated".toByteArray()) })
    }

    private fun create(name: String): VirtualFile = myFixture.tempDirFixture.createFile(name, "original")

    private fun change(action: () -> Unit): Boolean {
        restartRequested = false
        WriteCommandAction.runWriteCommandAction(project, action)
        return restartRequested
    }
}
