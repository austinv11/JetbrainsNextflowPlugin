package com.austinv11.nextflow.actions

import com.austinv11.nextflow.NextflowIcons
import com.austinv11.nextflow.util.NextflowEnvironmentUtils
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowManager
import org.jetbrains.plugins.terminal.TerminalTabState

class NextflowConsoleAction : AnAction("Nextflow Console", "Launch an interactive Nextflow console", NextflowIcons.FILE) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val actualBin = NextflowEnvironmentUtils.getExecutableNextflowCommand(project)

        try {
            val terminalManager = TerminalToolWindowManager.getInstance(project)
            val runner = terminalManager.terminalRunner
            val tabState = TerminalTabState().apply {
                myTabName = "Nextflow Console"
                myWorkingDirectory = project.basePath
            }
            val widget = terminalManager.createNewSession(runner, tabState, null)
            widget.sendCommandToExecute("$actualBin console")

            val toolWindowManager = ToolWindowManager.getInstance(project)
            val terminalWindow = toolWindowManager.getToolWindow("Terminal")
            terminalWindow?.activate(null)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}