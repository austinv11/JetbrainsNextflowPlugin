package com.austinv11.nextflow.lsp

import com.austinv11.nextflow.NextflowSettings
import com.intellij.notification.NotificationAction
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class NextflowLspUpdateCheckerTest : BasePlatformTestCase() {
    private lateinit var originalState: NextflowSettings.State
    private lateinit var root: File
    private lateinit var checker: NextflowLspUpdateChecker
    private var fetches = 0
    private var availableVersion = "26.04.3"
    private val now = TimeUnit.DAYS.toMillis(100)

    override fun setUp() {
        super.setUp()
        val settings = NextflowSettings.getInstance(project)
        originalState = settings.state.copy()
        settings.loadState(NextflowSettings.State(languageVersion = "26.04"))
        root = Files.createTempDirectory("nextflow-lsp-update-test-").toFile()
        checker = NextflowLspUpdateChecker(project)
        writeJar(File(root, "servers/26.04.2/language-server-all.jar"))
    }

    override fun tearDown() {
        try {
            checker.dispose()
            NextflowSettings.getInstance(project).loadState(originalState)
            FileUtil.delete(root)
        } finally {
            super.tearDown()
        }
    }

    fun testDailyCheckOffersActionsWithoutInstallingAndDoesNotRepeatHourly() {
        val cache = cache()
        checker.checkForUpdates(false, cache, now)
        val notice = checker.notification!!
        assertTrue(notice.title.contains("26.04.3"))
        assertEquals(listOf("Install update", "Dismiss"), notice.actions.map { it.templatePresentation.text })
        assertFalse(File(root, "servers/26.04.3/language-server-all.jar").exists())
        checker.checkForUpdates(false, cache, now + TimeUnit.HOURS.toMillis(1))
        assertEquals(1, fetches)
        assertSame(notice, checker.notification)
    }

    fun testDismissSuppressesSameVersionButOffersANewerPatch() {
        val cache = cache()
        checker.checkForUpdates(false, cache, now)
        val notice = checker.notification!!
        val dismiss = notice.actions.single { it.templatePresentation.text == "Dismiss" } as NotificationAction
        dismiss.actionPerformed(AnActionEvent.createFromAnAction(dismiss, null, ActionPlaces.UNKNOWN, DataContext.EMPTY_CONTEXT), notice)
        assertEquals("26.04.3", NextflowSettings.getInstance(project).state.dismissedLspUpdateVersion)
        assertTrue(notice.isExpired)
        checker.checkForUpdates(false, cache, now + TimeUnit.DAYS.toMillis(1))
        assertSame(notice, checker.notification)
        availableVersion = "26.04.4"
        checker.checkForUpdates(false, cache, now + TimeUnit.DAYS.toMillis(2))
        assertNotSame(notice, checker.notification)
        assertTrue(checker.notification!!.title.contains("26.04.4"))
    }

    fun testManualCheckCanOfferADismissedVersionEvenWhenPeriodicChecksAreDisabled() {
        val state = NextflowSettings.getInstance(project).state
        state.checkLspUpdates = false
        state.dismissedLspUpdateVersion = "26.04.3"
        val cache = cache()
        checker.checkForUpdates(false, cache, now)
        assertEquals(0, fetches)
        checker.checkForUpdates(true, cache, now)
        assertNotNull(checker.notification)
        assertEquals(1, fetches)
    }

    fun testCustomJarPreventsAutomaticAndManualManagedUpdateLookups() {
        NextflowSettings.getInstance(project).state.customLspJarPath = "custom-server.jar"
        val cache = cache()
        checker.checkForUpdates(false, cache, now)
        checker.checkForUpdates(true, cache, now)
        assertEquals(0, fetches)
        assertNull(checker.notification)
    }

    fun testChangedSettingsDuringLookupDiscardTheOldSeriesOffer() {
        val cache = cache { NextflowSettings.getInstance(project).state.languageVersion = "26.10" }
        checker.checkForUpdates(false, cache, now)
        assertNull(checker.notification)
        assertEquals(0L, NextflowSettings.getInstance(project).state.lastLspUpdateCheck)
    }

    private fun cache(onFetch: () -> Unit = {}) = NextflowLspServerCache(root,
        fetchPage = {
            fetches++
            onFetch()
            NextflowLspReleasePage(listOf(NextflowLspRelease(NextflowLspVersion.parse(availableVersion)!!, "test-download")), false)
        },
        download = { _, file -> writeJar(file) })

    private fun writeJar(file: File) {
        file.parentFile.mkdirs()
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes[Attributes.Name.MAIN_CLASS] = "nextflow.lsp.TestServer"
        }
        JarOutputStream(file.outputStream(), manifest).use { }
    }
}
