package com.austinv11.nextflow.nfcore

import com.austinv11.nextflow.util.NextflowEnvironmentUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.terminal.frontend.toolwindow.TerminalToolWindowTabsManager

object NfCoreRunner {
    fun executeAction(project: Project, action: NfCoreAction) {
        val args = getArgsForAction(project, action) ?: return
        executeCommandArgs(project, action.displayName, args)
    }

    fun executeCommandArgs(project: Project, displayName: String, args: List<String>) {
        val actualBin = NextflowEnvironmentUtils.getExecutableNfCoreCommand(project)
        val command = "$actualBin ${args.joinToString(" ")}"

        try {
            val tabBuilder = TerminalToolWindowTabsManager.getInstance(project).createTabBuilder()
                .tabName("nf-core: $displayName")
                .requestFocus(true)
                .deferSessionStartUntilUiShown(true)
            project.basePath?.let { tabBuilder.workingDirectory(it) }
            tabBuilder.createTab().view.createSendTextBuilder().shouldExecute().send(command)

            val toolWindowManager = ToolWindowManager.getInstance(project)
            val terminalWindow = toolWindowManager.getToolWindow("Terminal")
            terminalWindow?.activate(null)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    private fun getArgsForAction(project: Project, action: NfCoreAction): List<String>? {
        when (action.commandType) {
            "create" -> {
                val dialog = CreatePipelineDialog(project)
                if (dialog.showAndGet()) return dialog.getCommandArguments()
            }
            "download" -> {
                val dialog = DownloadPipelineDialog(project)
                if (dialog.showAndGet()) return dialog.getCommandArguments()
            }
            "launch" -> {
                val dialog = LaunchPipelineDialog(project)
                if (dialog.showAndGet()) return dialog.getCommandArguments()
            }
            "lint" -> {
                return listOf("lint")
            }
            "modules create" -> {
                val dialog = ModulesCreateDialog(project)
                if (dialog.showAndGet()) return dialog.getCommandArguments()
            }
            "modules install" -> {
                val dialog = ManageItemDialog(project, "Install", "Modules")
                if (dialog.showAndGet()) return dialog.getCommandArguments()
            }
            "modules update" -> {
                val dialog = ManageItemDialog(project, "Update", "Modules")
                if (dialog.showAndGet()) return dialog.getCommandArguments()
            }
            "modules remove" -> {
                val dialog = ManageItemDialog(project, "Remove", "Modules")
                if (dialog.showAndGet()) return dialog.getCommandArguments()
            }
            "subworkflows create" -> {
                val dialog = SubworkflowsCreateDialog(project)
                if (dialog.showAndGet()) return dialog.getCommandArguments()
            }
            "subworkflows install" -> {
                val dialog = ManageItemDialog(project, "Install", "Subworkflows")
                if (dialog.showAndGet()) return dialog.getCommandArguments()
            }
            "subworkflows update" -> {
                val dialog = ManageItemDialog(project, "Update", "Subworkflows")
                if (dialog.showAndGet()) return dialog.getCommandArguments()
            }
            "subworkflows remove" -> {
                val dialog = ManageItemDialog(project, "Remove", "Subworkflows")
                if (dialog.showAndGet()) return dialog.getCommandArguments()
            }
        }
        return null
    }
}
