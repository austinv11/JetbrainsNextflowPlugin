package com.austinv11.nextflow.execution.console

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.icons.AllIcons
import javax.swing.JTree
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.treeStructure.Tree
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import java.awt.BorderLayout
import java.io.File

class NextflowConsoleView(val project: Project, val rawConsole: ConsoleView) : ConsoleView {

    private val mainPanel = JPanel(BorderLayout())
    private val tabbedPane = JBTabbedPane()

    // Weblog Tree View UI
    private val treeRoot = DefaultMutableTreeNode("Nextflow Run")
    private val treeModel = DefaultTreeModel(treeRoot)
    private val tree = Tree(treeModel)

    // Log Viewer for tasks
    private val logTextArea = JTextArea()

    // Maps
    private val processNodes = mutableMapOf<String, DefaultMutableTreeNode>()
    private val taskNodes = mutableMapOf<String, DefaultMutableTreeNode>()

    init {
        logTextArea.isEditable = false


        tree.cellRenderer = object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(
                tree: JTree,
                value: Any?,
                selected: Boolean,
                expanded: Boolean,
                leaf: Boolean,
                row: Int,
                hasFocus: Boolean
            ) {
                val node = value as? DefaultMutableTreeNode ?: return
                val userObject = node.userObject

                if (userObject is String) {
                    // This is the root or a process node
                    append(userObject, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    icon = if (userObject == "Nextflow Run") AllIcons.RunConfigurations.TestState.Run else AllIcons.Nodes.Folder
                } else if (userObject is NextflowTaskData) {
                    val statusColor = when (userObject.status) {
                        "COMPLETED" -> SimpleTextAttributes.REGULAR_ATTRIBUTES
                        "FAILED" -> SimpleTextAttributes.ERROR_ATTRIBUTES
                        "RUNNING" -> SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, com.intellij.ui.JBColor.BLUE)
                        else -> SimpleTextAttributes.GRAY_ATTRIBUTES
                    }

                    val iconState = when (userObject.status) {
                        "COMPLETED" -> AllIcons.RunConfigurations.TestPassed
                        "FAILED" -> AllIcons.RunConfigurations.TestFailed
                        "RUNNING" -> AllIcons.RunConfigurations.TestState.Run
                        else -> AllIcons.RunConfigurations.TestIgnored
                    }

                    append(userObject.name, statusColor)
                    append(" [" + userObject.status + "]", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                    icon = iconState
                }
            }
        }

        val splitter = JBSplitter(false, 0.3f)
        splitter.firstComponent = JBScrollPane(tree)
        splitter.secondComponent = JBScrollPane(logTextArea)

        val weblogPanel = JPanel(BorderLayout())
        weblogPanel.add(splitter, BorderLayout.CENTER)

        tabbedPane.addTab("Weblog Tree", weblogPanel)
        tabbedPane.addTab("Raw Console", rawConsole.component)

        mainPanel.add(tabbedPane, BorderLayout.CENTER)

        // Tree Selection Listener to load logs
        tree.addTreeSelectionListener { e ->
            val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
            if (node != null) {
                val nodeData = node.userObject
                if (nodeData is NextflowTaskData) {
                    loadTaskLogs(nodeData)
                } else {
                    logTextArea.text = ""
                }
            }
        }
    }

    private fun loadTaskLogs(taskData: NextflowTaskData) {
        val workdir = taskData.workdir
        if (workdir != null && workdir.startsWith("/")) {
            // Local file execution
            val out = File(workdir, ".command.out")
            val err = File(workdir, ".command.err")
            val log = File(workdir, ".command.log")

            val sb = StringBuilder()
            if (out.exists()) {
                sb.append("--- .command.out ---\n").append(out.readText()).append("\n\n")
            }
            if (err.exists()) {
                sb.append("--- .command.err ---\n").append(err.readText()).append("\n\n")
            }
            if (log.exists()) {
                sb.append("--- .command.log ---\n").append(log.readText()).append("\n\n")
            }
            if (!out.exists() && !err.exists() && !log.exists()) {
                sb.append("No local logs found in ").append(workdir)
            }
            logTextArea.text = sb.toString()
            logTextArea.caretPosition = 0
        } else {
            logTextArea.text = "Remote execution or invalid workdir:\n" + (workdir ?: "Unknown") + "\nPlease check cloud storage or Nextflow standard console for logs."
        }
    }

    fun handleWeblogEvent(event: JsonNode) {
        SwingUtilities.invokeLater {
            val type = event.get("event")?.asText() ?: return@invokeLater
            val trace = event.get("trace") ?: return@invokeLater

            when (type) {
                "process_submitted", "process_started", "process_completed", "process_failed" -> {
                    val processName = trace.get("process")?.asText() ?: return@invokeLater
                    val taskName = trace.get("name")?.asText() ?: return@invokeLater
                    val workdir = trace.get("workdir")?.asText()
                    val status = trace.get("status")?.asText() ?: "UNKNOWN"

                    val processNode = processNodes.getOrPut(processName) {
                        val node = DefaultMutableTreeNode(processName)
                        treeModel.insertNodeInto(node, treeRoot, treeRoot.childCount)
                        node
                    }

                    val taskId = trace.get("task_id")?.asText() ?: taskName
                    val taskData = NextflowTaskData(taskName, taskId, workdir, status)

                    var taskNode = taskNodes[taskId]
                    if (taskNode == null) {
                        taskNode = DefaultMutableTreeNode(taskData)
                        taskNodes[taskId] = taskNode
                        treeModel.insertNodeInto(taskNode, processNode, processNode.childCount)
                    } else {
                        taskNode.userObject = taskData
                        treeModel.nodeChanged(taskNode)
                    }

                    // Auto-expand process node
                    tree.expandPath(javax.swing.tree.TreePath(processNode.path))
                }
            }
        }
    }

    override fun dispose() {
        rawConsole.dispose()
    }

    override fun getComponent(): JComponent = mainPanel

    override fun getPreferredFocusableComponent(): JComponent = tabbedPane

    override fun print(text: String, contentType: ConsoleViewContentType) {
        rawConsole.print(text, contentType)
    }

    override fun clear() {
        rawConsole.clear()
    }

    override fun scrollTo(offset: Int) {
        rawConsole.scrollTo(offset)
    }

    override fun attachToProcess(processHandler: com.intellij.execution.process.ProcessHandler) {
        rawConsole.attachToProcess(processHandler)
    }

    override fun setOutputPaused(value: Boolean) {
        rawConsole.isOutputPaused = value
    }

    override fun isOutputPaused(): Boolean = rawConsole.isOutputPaused

    override fun hasDeferredOutput(): Boolean = rawConsole.hasDeferredOutput()

    override fun performWhenNoDeferredOutput(runnable: Runnable) {
        rawConsole.performWhenNoDeferredOutput(runnable)
    }

    override fun setHelpId(helpId: String) {
        rawConsole.setHelpId(helpId)
    }

    override fun addMessageFilter(filter: com.intellij.execution.filters.Filter) {
        rawConsole.addMessageFilter(filter)
    }

    override fun printHyperlink(hyperlinkText: String, info: com.intellij.execution.filters.HyperlinkInfo?) {
        rawConsole.printHyperlink(hyperlinkText, info)
    }

    override fun getContentSize(): Int = rawConsole.contentSize

    override fun canPause(): Boolean = rawConsole.canPause()

    override fun createConsoleActions(): Array<AnAction> = rawConsole.createConsoleActions()

    override fun allowHeavyFilters() {
        rawConsole.allowHeavyFilters()
    }
}


data class NextflowTaskData(
    val name: String,
    val taskId: String,
    val workdir: String?,
    val status: String
) {
    override fun toString(): String {
        return "[$status] $name"
    }
}
