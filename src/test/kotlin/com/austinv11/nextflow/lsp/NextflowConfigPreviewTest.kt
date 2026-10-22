package com.austinv11.nextflow.lsp

import com.google.gson.JsonParser
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckBoxList
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import org.eclipse.lsp4j.Command
import java.awt.event.KeyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class NextflowConfigPreviewTest : BasePlatformTestCase() {
    fun testCodeLensArgumentsBecomeTheFourArgumentServerRequest() {
        val command = Command("Preview config", "nextflow.previewConfig", listOf(
            JsonParser.parseString("\"file:///C%3A/pipeline/main.nf\""), JsonParser.parseString("\"EXAMPLE\"")
        ))
        val params = NextflowConfigPreviewRequest.fromCommand(command).toParams()
        assertEquals("nextflow.server.previewConfig", params.command)
        assertEquals(listOf("file:///C:/pipeline/main.nf", "EXAMPLE", emptyList<String>(), "EXAMPLE"), params.arguments)
    }

    fun testExplicitProfilesAndQualifiedProcessNameArePreserved() {
        val command = Command("Preview config", "nextflow.previewConfig", listOf(
            "file:///pipeline/main.nf", "EXAMPLE", listOf("docker", "large"), "WORKFLOW:EXAMPLE"
        ))
        val request = NextflowConfigPreviewRequest.fromCommand(command)
        assertEquals(listOf("docker", "large"), request.profiles)
        assertEquals("WORKFLOW:EXAMPLE", request.qualifiedName)
        assertEquals(command.arguments, request.toParams().arguments)
    }

    fun testMissingCodeLensArgumentsFailWithAnActionableMessage() {
        val error = expectFailure<IllegalArgumentException> {
            NextflowConfigPreviewRequest.fromCommand(Command("Preview config", "nextflow.previewConfig"))
        }
        assertTrue(error.message.orEmpty().contains("source file or process name"))
    }

    fun testStructuredResponseShowsWinningAndOverriddenDirectiveSources() {
        val preview = response()
        val text = preview.renderText()
        assertEquals("EXAMPLE", preview.process)
        assertEquals(listOf("large"), preview.labels)
        assertEquals(listOf("docker"), preview.profiles)
        assertTrue(text.contains("cpus = 8\n    From base.config:4 (withLabel:large)"))
        assertTrue(text.contains("Overridden: cpus = 2\n    From main.nf:3 (process)"))
        assertTrue(text.indexOf("cpus = 8") < text.indexOf("cpus = 2"))
    }

    fun testRepeatableDirectivesAndProfileOriginsRemainVisible() {
        val preview = response().copy(activeProfiles = listOf("docker"), directives = listOf(
            NextflowConfigPreview.Directive("publishDir", listOf(
                NextflowConfigPreview.Layer("'first'", "process", "docker", "nextflow.config", 5, true),
                NextflowConfigPreview.Layer("'second'", "process", null, "main.nf", 2, true)
            ))
        ))
        val text = preview.renderText()
        assertTrue(text.contains("Active profiles: docker"))
        assertTrue(text.contains("publishDir = 'first'"))
        assertTrue(text.contains("publishDir = 'second'"))
        assertTrue(text.contains("profile: docker"))
        assertFalse(text.contains("Overridden:"))
    }

    fun testServerErrorsAreShownInsteadOfAnEmptyPreview() {
        val error = expectFailure<IllegalStateException> {
            NextflowConfigPreview.fromResponse(mapOf("error" to "The workspace has no nextflow.config file."))
        }
        assertEquals("The workspace has no nextflow.config file.", error.message)
    }

    fun testInvalidServerResponsesAreRejectedAndEmptyDirectivesAreValid() {
        for (raw in listOf(null, "unexpected", mapOf("result" to mapOf("process" to "EXAMPLE")))) {
            expectFailure<IllegalArgumentException> { NextflowConfigPreview.fromResponse(raw) }
        }
        val empty = NextflowConfigPreview.fromResponse(mapOf("result" to mapOf("process" to "EXAMPLE", "directives" to emptyList<Any>())))
        assertTrue(empty.renderText().contains("No matching process directives."))
    }

    fun testPreviewIsReadOnlyAndProfileSelectionsRefreshAutomatically() {
        val requests = CopyOnWriteArrayList<NextflowConfigPreviewRequest>()
        val dialog = NextflowConfigPreviewDialog(project, request()) {
            requests.add(it)
            response().copy(profiles = listOf("docker", "large", "docker"), activeProfiles = it.profiles)
        }
        try {
            dialog.refreshPreview()
            awaitLoaded(dialog)
            assertFalse(dialog.previewText.isEditable)
            assertTrue(dialog.previewText.text.contains("cpus = 8"))
            assertEquals(listOf("docker", "large"), profileItems(dialog).map { it.text })
            selectProfile(dialog, "docker")
            selectProfile(dialog, "large")
            awaitLoaded(dialog)
            assertEquals(listOf("docker", "large"), requests.last().profiles)
            assertEquals("Rapid selections should be combined into one refresh", 2, requests.size)
            assertTrue(dialog.previewText.text.contains("Active profiles: docker, large"))
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun testProfilesCanBeDeselectedReorderedAndClearedFromTheDropdown() {
        val requests = CopyOnWriteArrayList<NextflowConfigPreviewRequest>()
        val dialog = NextflowConfigPreviewDialog(project, request()) {
            requests.add(it)
            response().copy(profiles = listOf("small", "large"), activeProfiles = it.profiles)
        }
        try {
            dialog.refreshPreview()
            awaitLoaded(dialog)
            assertEquals("Select profiles", dialog.profilesSelector.text)
            selectProfile(dialog, "small")
            selectProfile(dialog, "large")
            assertTrue(profileItems(dialog).all { it.isSelected })
            selectProfile(dialog, "small")
            selectProfile(dialog, "small")
            awaitLoaded(dialog)
            assertEquals(listOf("large", "small"), requests.last().profiles)
            assertEquals("large, small", dialog.profilesSelector.text)

            dialog.profilesSelector.clearProfiles(dialog.profilesSelector.createProfileList())
            awaitLoaded(dialog)
            assertEquals(emptyList<String>(), requests.last().profiles)
            assertTrue(profileItems(dialog).none { it.isSelected })
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun testProfileSuggestionsUpdateAfterRefreshAndRemovedSelectionsCanBeCleared() {
        var profiles = listOf("docker")
        val dialog = NextflowConfigPreviewDialog(project, request()) {
            response().copy(profiles = profiles, activeProfiles = it.profiles)
        }
        try {
            dialog.refreshPreview()
            awaitLoaded(dialog)
            selectProfile(dialog, "docker")
            profiles = listOf("large")
            dialog.refreshButton.doClick()
            awaitLoaded(dialog)
            assertEquals(listOf("large", "docker (unavailable)"), profileItems(dialog).map { it.text })
            assertTrue(profileItems(dialog).single { it.text == "docker (unavailable)" }.isSelected)
            selectProfile(dialog, "docker")
            assertEquals(listOf("large"), profileItems(dialog).map { it.text })
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun testNoProfilesDisablesTheDropdownButStillAllowsRefresh() {
        val dialog = NextflowConfigPreviewDialog(project, request()) { response().copy(profiles = emptyList()) }
        try {
            dialog.refreshPreview()
            awaitLoaded(dialog)
            assertFalse(dialog.profilesSelector.isEnabled)
            assertEquals("No profiles available", dialog.profilesSelector.text)
            assertTrue(dialog.refreshButton.isEnabled)
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun testProfilePopupKeepsTheSameListForMultipleToggles() {
        val dialog = NextflowConfigPreviewDialog(project, request()) {
            response().copy(profiles = listOf("small", "large"), activeProfiles = it.profiles)
        }
        try {
            dialog.refreshPreview()
            awaitLoaded(dialog)
            val popup = dialog.profilesSelector.createPopup()
            try {
                @Suppress("UNCHECKED_CAST")
                val list = UIUtil.findComponentOfType(popup.content, CheckBoxList::class.java) as CheckBoxList<String>
                toggleProfile(list, "small")
                toggleProfile(list, "large")
                assertEquals(listOf("small", "large"), list.checkedItems)
                assertFalse("Selecting profiles must not dispose the popup", popup.isDisposed)
                awaitLoaded(dialog)
                assertEquals(listOf("small", "large"), dialog.profilesSelector.selectedProfiles)
            } finally {
                popup.cancel()
            }
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun testChangingProfilesWhileLoadingRefreshesTheLatestSelection() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val latestStarted = CountDownLatch(1)
        val releaseLatest = CountDownLatch(1)
        val requests = CopyOnWriteArrayList<List<String>>()
        val dialog = NextflowConfigPreviewDialog(project, request()) {
            requests.add(it.profiles)
            if (it.profiles == listOf("small")) {
                started.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            if (it.profiles == listOf("small", "large")) {
                latestStarted.countDown()
                check(releaseLatest.await(5, TimeUnit.SECONDS))
            }
            response().copy(profiles = listOf("small", "large"), activeProfiles = it.profiles)
        }
        try {
            dialog.refreshPreview()
            awaitLoaded(dialog)
            selectProfile(dialog, "small")
            awaitCondition { started.count == 0L }
            assertTrue("Profile selection must remain enabled while loading", dialog.profilesSelector.isEnabled)
            selectProfile(dialog, "large")
            release.countDown()
            awaitCondition { latestStarted.count == 0L }
            assertFalse("An older response must not replace the current preview", dialog.previewText.text.contains("Active profiles: small"))
            releaseLatest.countDown()
            awaitLoaded(dialog)
            assertEquals(listOf(emptyList(), listOf("small"), listOf("small", "large")), requests.toList())
            assertTrue(dialog.previewText.text.contains("Active profiles: small, large"))
        } finally {
            release.countDown()
            releaseLatest.countDown()
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun testFailedPreviewCanBeRefreshedSuccessfully() {
        var fail = true
        val dialog = NextflowConfigPreviewDialog(project, request()) {
            if (fail) error("Fix the syntax error in base.config.")
            response()
        }
        try {
            dialog.refreshPreview()
            awaitLoaded(dialog)
            assertTrue(dialog.previewText.text.contains("Fix the syntax error in base.config."))
            fail = false
            dialog.refreshButton.doClick()
            awaitLoaded(dialog)
            assertTrue(dialog.previewText.text.contains("cpus = 8"))
            assertFalse(dialog.previewText.text.contains("Unable to load"))
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    private fun request() = NextflowConfigPreviewRequest("file:///pipeline/main.nf", "EXAMPLE")

    private fun profileItems(dialog: NextflowConfigPreviewDialog) =
        dialog.profilesSelector.createProfileList().let { list -> (0 until list.model.size).map { list.model.getElementAt(it) } }

    private fun selectProfile(dialog: NextflowConfigPreviewDialog, profile: String) {
        toggleProfile(dialog.profilesSelector.createProfileList(), profile)
    }

    private fun toggleProfile(list: CheckBoxList<String>, profile: String) {
        list.selectedIndex = (0 until list.model.size).single { list.getItemAt(it) == profile }
        val event = KeyEvent(list, KeyEvent.KEY_TYPED, System.currentTimeMillis(), 0, KeyEvent.VK_UNDEFINED, ' ')
        list.keyListeners.forEach { it.keyTyped(event) }
    }

    private inline fun <reified T : Throwable> expectFailure(action: () -> Any?): T {
        try {
            action()
        } catch (e: Throwable) {
            if (e is T) return e
            throw e
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }

    private fun response() = NextflowConfigPreview.fromResponse(JsonParser.parseString("""
        {"result": {"process": "EXAMPLE", "labels": ["large"], "profiles": ["docker"], "activeProfiles": [],
          "directives": [{"name": "cpus", "layers": [
            {"value": "8", "source": "withLabel:large", "profile": null, "file": "base.config", "line": 4, "active": true},
            {"value": "2", "source": "process", "profile": null, "file": "main.nf", "line": 3, "active": false}
          ]}]}}
    """.trimIndent()))

    private fun awaitLoaded(dialog: NextflowConfigPreviewDialog) {
        awaitCondition { dialog.refreshButton.isEnabled }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5000
        while (!condition() && System.currentTimeMillis() < deadline) {
            UIUtil.dispatchAllInvocationEvents()
            Thread.sleep(10)
        }
        assertTrue("Preview request did not finish", condition())
    }
}
