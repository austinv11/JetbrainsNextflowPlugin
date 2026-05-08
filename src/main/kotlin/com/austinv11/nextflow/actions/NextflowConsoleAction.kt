package com.austinv11.nextflow.actions

import com.austinv11.nextflow.NextflowIcons
import com.austinv11.nextflow.NextflowSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

class NextflowConsoleAction : AnAction("Nextflow Console", "Launch an interactive Nextflow console", NextflowIcons.FILE) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = NextflowSettings.getInstance(project)
        val nextflowBin = settings.state.nextflowBinaryPath.takeIf { it.isNotBlank() } ?: "nextflow"
        val actualBin = if (SystemInfo.isWindows) "wsl $nextflowBin" else nextflowBin

        try {
            val terminalManager = TerminalToolWindowManager.getInstance(project)
            @Suppress("DEPRECATION")
            val widget = terminalManager.createLocalShellWidget(project.basePath, "Nextflow Console")
            widget.executeCommand("$actualBin console")

            val toolWindowManager = ToolWindowManager.getInstance(project)
            val terminalWindow = toolWindowManager.getToolWindow("Terminal")
            terminalWindow?.activate(null)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}
