package com.austinv11.nextflow

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class NextflowCompletionAutoPopupHandlerTest : BasePlatformTestCase() {

    fun testAutoPopupIsTriggeredForDotAndColon() {
        myFixture.configureByText("test.nf", "process foo { }")

        val handler = NextflowCompletionAutoPopupHandler()

        // Trigger auto popup for '.'
        var result = handler.checkAutoPopup('.', project, myFixture.editor, myFixture.file)
        assertEquals(TypedHandlerDelegate.Result.CONTINUE, result)

        // Trigger auto popup for ':'
        result = handler.checkAutoPopup(':', project, myFixture.editor, myFixture.file)
        assertEquals(TypedHandlerDelegate.Result.CONTINUE, result)

        // Ensure other characters do not trigger it and pass it to normal
        result = handler.checkAutoPopup('a', project, myFixture.editor, myFixture.file)
        assertEquals(TypedHandlerDelegate.Result.CONTINUE, result)
    }
}
