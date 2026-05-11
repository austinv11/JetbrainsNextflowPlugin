package com.austinv11.nextflow.nfcore

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

abstract class NfCoreDialogWrapper(project: Project, title: String) : DialogWrapper(project, true) {
    init {
        setTitle(title)
    }

    abstract fun getCommandArguments(): List<String>
}

class CreatePipelineDialog(project: Project) : NfCoreDialogWrapper(project, "Create Pipeline") {
    var pipelineName: String = ""
    var description: String = ""
    var author: String = ""

    init {
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row("Pipeline Name:") {
                textField().bindText(::pipelineName).focused()
            }
            row("Description:") {
                textField().bindText(::description)
            }
            row("Author:") {
                textField().bindText(::author)
            }
        }
    }

    override fun getCommandArguments(): List<String> {
        val args = mutableListOf("create", "-n", pipelineName)
        if (description.isNotBlank()) args.addAll(listOf("-d", description))
        if (author.isNotBlank()) args.addAll(listOf("-a", author))
        return args
    }
}

class ModulesCreateDialog(project: Project) : NfCoreDialogWrapper(project, "Create Module") {
    var toolName: String = ""
    var author: String = ""

    init {
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row("Tool Name:") {
                textField().bindText(::toolName).focused()
            }
            row("Author:") {
                textField().bindText(::author)
            }
        }
    }

    override fun getCommandArguments(): List<String> {
        val args = mutableListOf("modules", "create", toolName)
        if (author.isNotBlank()) args.addAll(listOf("-a", author))
        return args
    }
}

class SubworkflowsCreateDialog(project: Project) : NfCoreDialogWrapper(project, "Create Subworkflow") {
    var subworkflowName: String = ""
    var author: String = ""

    init {
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row("Subworkflow Name:") {
                textField().bindText(::subworkflowName).focused()
            }
            row("Author:") {
                textField().bindText(::author)
            }
        }
    }

    override fun getCommandArguments(): List<String> {
        val args = mutableListOf("subworkflows", "create", subworkflowName)
        if (author.isNotBlank()) args.addAll(listOf("-a", author))
        return args
    }
}

class ManageItemDialog(project: Project, private val actionType: String, private val itemType: String) : NfCoreDialogWrapper(project, "$actionType $itemType") {
    var itemName: String = ""

    init {
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row("$itemType Name:") {
                textField().bindText(::itemName).focused()
            }
        }
    }

    override fun getCommandArguments(): List<String> {
        return listOf(itemType.lowercase(), actionType.lowercase(), itemName)
    }
}

class DownloadPipelineDialog(project: Project) : NfCoreDialogWrapper(project, "Download Pipeline") {
    var pipelineName: String = ""
    var revision: String = ""
    var outdir: String = ""

    init {
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row("Pipeline Name:") {
                textField().bindText(::pipelineName).focused()
            }
            row("Revision (Optional):") {
                textField().bindText(::revision)
            }
            row("Output Directory (Optional):") {
                textField().bindText(::outdir)
            }
        }
    }

    override fun getCommandArguments(): List<String> {
        val args = mutableListOf("download", pipelineName)
        if (revision.isNotBlank()) args.addAll(listOf("-r", revision))
        if (outdir.isNotBlank()) args.addAll(listOf("-o", outdir))
        return args
    }
}
