package com.austinv11.nextflow.actions

import com.austinv11.nextflow.NextflowIcons
import com.austinv11.nextflow.util.NextflowEnvironmentUtils
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

class NextflowConsoleAction : AnAction("Nextflow Console", "Launch an interactive Nextflow console", NextflowIcons.FILE) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val actualBin = NextflowEnvironmentUtils.getExecutableNextflowCommand(project)

        try {
            val terminalManager = TerminalToolWindowManager.getInstance(project)
            val widget = terminalManager.createShellWidget(project.basePath, "Nextflow Console", true, true)
            (widget as org.jetbrains.plugins.terminal.ShellTerminalWidget).executeCommand("$actualBin console")

            val toolWindowManager = ToolWindowManager.getInstance(project)
            val terminalWindow = toolWindowManager.getToolWindow("Terminal")
            terminalWindow?.activate(null)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}
