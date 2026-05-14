package com.austinv11.nextflow.execution

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.filters.TextConsoleBuilderFactory

class NextflowConsoleViewTest : BasePlatformTestCase() {

    fun testConsoleViewInstantiation() {
        ApplicationManager.getApplication().invokeAndWait {
            val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
            val consoleView = com.austinv11.nextflow.execution.console.NextflowConsoleView(project, console, "/tmp")
            assertNotNull(consoleView.component)
            assertNotNull(consoleView.preferredFocusableComponent)
            consoleView.clear()
            consoleView.scrollTo(0)
            consoleView.dispose()
        }
    }
}
