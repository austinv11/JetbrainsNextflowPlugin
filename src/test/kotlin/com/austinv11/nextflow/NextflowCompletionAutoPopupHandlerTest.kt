package com.austinv11.nextflow

import com.intellij.codeInsight.AutoPopupController
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NextflowCompletionAutoPopupHandlerTest : BasePlatformTestCase() {

    fun testAutoPopupIsTriggeredForDot() {
        myFixture.configureByText("test.nf", "process foo <caret>")

        // Clear the user data just in case
        myFixture.editor.putUserData(AutoPopupController.ALWAYS_AUTO_POPUP, null)

        // Type the character to trigger the typed handler
        myFixture.type('.')

        // Check if the handler set the data on the editor
        val popupData = myFixture.editor.getUserData(AutoPopupController.ALWAYS_AUTO_POPUP)
        assertEquals(true, popupData)
    }

    fun testAutoPopupIsTriggeredForColon() {
        myFixture.configureByText("test.nf", "process foo <caret>")

        myFixture.editor.putUserData(AutoPopupController.ALWAYS_AUTO_POPUP, null)

        myFixture.type(':')

        val popupData = myFixture.editor.getUserData(AutoPopupController.ALWAYS_AUTO_POPUP)
        assertEquals(true, popupData)
    }

    fun testAutoPopupIsNotTriggeredForOtherCharacters() {
        myFixture.configureByText("test.nf", "process foo <caret>")

        myFixture.editor.putUserData(AutoPopupController.ALWAYS_AUTO_POPUP, null)

        myFixture.type('a')

        val popupData = myFixture.editor.getUserData(AutoPopupController.ALWAYS_AUTO_POPUP)
        assertNull(popupData)
    }
}
