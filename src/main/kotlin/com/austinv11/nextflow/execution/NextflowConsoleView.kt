package com.austinv11.nextflow.execution.console

import com.fasterxml.jackson.databind.JsonNode
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.icons.AllIcons
import com.intellij.ui.AnimatedIcon
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

import java.util.Timer
import java.util.TimerTask
import java.io.RandomAccessFile

import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

class NextflowConsoleView(val project: Project, val rawConsole: ConsoleView, val runDir: String? = null) : ConsoleView {

    private val mainPanel = JPanel(BorderLayout())
    private val tabbedPane = JBTabbedPane()

    // Process Tree View UI
    private val treeRoot = DefaultMutableTreeNode("Nextflow Run")
    private val treeModel = DefaultTreeModel(treeRoot)
    private val tree = Tree(treeModel)

    // Log Viewer for tasks
    private val logTextArea = JTextArea()

    // Log Tailer
    private var logTailTimer: Timer? = null
    private var lastLogPosition: Long = 0


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
                        "RUNNING" -> AnimatedIcon.Default.INSTANCE
                        else -> AllIcons.RunConfigurations.TestIgnored
                    }

                    append(userObject.name, statusColor)
                    var statusText = " [" + userObject.status
                    if (userObject.status == "COMPLETED" && userObject.durationMs != null) {
                        statusText += " in ${formatDuration(userObject.durationMs)}"
                    }
                    statusText += "]"
                    append(statusText, SimpleTextAttributes.GRAYED_ATTRIBUTES)
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

        tabbedPane.addTab("Process Tree", weblogPanel)
        tabbedPane.addTab("Raw Console", rawConsole.component)

        mainPanel.add(tabbedPane, BorderLayout.CENTER)

        // Tree Selection Listener to load logs
        tree.addTreeSelectionListener { e ->
            val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode

            // Stop any existing tailer when selection changes
            stopLogTail()

            if (node != null) {
                val nodeData = node.userObject
                if (nodeData is NextflowTaskData) {
                    loadTaskLogs(nodeData)
                } else if (nodeData is String && nodeData == "Nextflow Run") {
                    loadRunLogs()
                } else if (nodeData is String) {
                    loadProcessLogs(node)
                } else {
                    logTextArea.text = ""
                }
            }
        }

        // Mouse Listener for Context Menu
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    handlePopup(e)
                }
            }
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    handlePopup(e)
                }
            }

            private fun handlePopup(e: MouseEvent) {
                val path = tree.getPathForLocation(e.x, e.y) ?: return
                tree.selectionPath = path
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                val nodeData = node.userObject as? NextflowTaskData ?: return

                val workdir = nodeData.workdir
                val convertedWorkdir = com.austinv11.nextflow.util.NextflowEnvironmentUtils.convertFromWslPathIfNeeded(workdir ?: "")
                if (convertedWorkdir.isEmpty()) return

                val actionGroup = DefaultActionGroup()

                actionGroup.add(object : AnAction("Open Work Directory", "Open the task work directory in file explorer", AllIcons.Nodes.Folder) {
                    override fun actionPerformed(event: AnActionEvent) {
                        val file = File(convertedWorkdir)
                        if (file.exists()) {
                            RevealFileAction.openDirectory(file)
                        }
                    }
                })

                actionGroup.add(object : AnAction("Copy Work Directory Path", "Copy the path to the clipboard", AllIcons.Actions.Copy) {
                    override fun actionPerformed(event: AnActionEvent) {
                        CopyPasteManager.getInstance().setContents(StringSelection(convertedWorkdir))
                    }
                })

                actionGroup.add(object : AnAction("Open .command.sh", "Open the task execution script in the editor", AllIcons.FileTypes.Text) {
                    override fun actionPerformed(event: AnActionEvent) {
                        val scriptFile = File(convertedWorkdir, ".command.sh")
                        if (scriptFile.exists()) {
                            val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(scriptFile)
                            if (vFile != null) {
                                FileEditorManager.getInstance(project).openFile(vFile, true)
                            }
                        }
                    }
                })

                val popupMenu = ActionManager.getInstance().createActionPopupMenu("ProcessTreePopup", actionGroup)
                popupMenu.component.show(tree, e.x, e.y)
            }
        })
    }

    private fun loadRunLogs() {
        logTextArea.text = ""
        val dir = runDir ?: project.basePath ?: return
        val convertedDir = com.austinv11.nextflow.util.NextflowEnvironmentUtils.convertFromWslPathIfNeeded(dir)
        val logFile = File(convertedDir, ".nextflow.log")

        if (!logFile.exists()) {
            logTextArea.text = "Waiting for .nextflow.log to be created at ${logFile.absolutePath}...\n"
        } else {
            // Read initial content
            try {
                logTextArea.text = logFile.readText()
                lastLogPosition = logFile.length()
            } catch (e: Exception) {
                logTextArea.text = "Error reading log: ${e.message}\n"
            }
        }

        logTailTimer = Timer("NextflowLogTailer", true).apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (!logFile.exists()) return

                    try {
                        val currentLen = logFile.length()
                        if (currentLen > lastLogPosition) {
                            RandomAccessFile(logFile, "r").use { raf ->
                                raf.seek(lastLogPosition)
                                val bytesToRead = (currentLen - lastLogPosition).toInt()
                                val buffer = ByteArray(bytesToRead)
                                raf.readFully(buffer)
                                val newText = String(buffer, Charsets.UTF_8)

                                SwingUtilities.invokeLater {
                                    logTextArea.append(newText)
                                    // Optional: auto-scroll to bottom
                                    // logTextArea.caretPosition = logTextArea.document.length
                                }
                                lastLogPosition = currentLen
                            }
                        } else if (currentLen < lastLogPosition) {
                            // File was rotated or truncated
                            lastLogPosition = 0
                        }
                    } catch (e: Exception) {
                        // Ignore read errors during tail
                    }
                }
            }, 1000, 1000)
        }
    }

    private fun stopLogTail() {
        logTailTimer?.cancel()
        logTailTimer = null
        lastLogPosition = 0
    }

    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        val s = seconds % 60
        val m = minutes % 60

        return buildString {
            if (hours > 0) append("${hours}h ")
            if (m > 0 || hours > 0) append("${m}m ")
            if (s > 0 || (hours == 0L && m == 0L)) append("${s}s")
            if (isEmpty()) append("< 1s")
        }.trim()
    }

    private fun loadTaskLogs(taskData: NextflowTaskData) {
        val workdir = taskData.workdir
        val convertedWorkdir = com.austinv11.nextflow.util.NextflowEnvironmentUtils.convertFromWslPathIfNeeded(workdir ?: "")
        if (convertedWorkdir.isNotEmpty()) {
            val logFile = File(convertedWorkdir, ".command.log")
            if (logFile.exists()) {
                logTextArea.text = logFile.readText()
            } else {
                logTextArea.text = "No log file found at ${logFile.absolutePath}"
            }
            logTextArea.caretPosition = 0
        } else {
            logTextArea.text = "Remote execution or invalid workdir:\n" + (convertedWorkdir.takeIf { it.isNotEmpty() } ?: "Unknown") + "\nPlease check cloud storage or Nextflow standard console for logs."
        }
    }

    private fun loadProcessLogs(processNode: DefaultMutableTreeNode) {
        val sb = java.lang.StringBuilder()
        for (i in 0 until processNode.childCount) {
            val childNode = processNode.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val taskData = childNode.userObject as? NextflowTaskData ?: continue

            sb.append("--- [${taskData.name}] ---\n")
            val workdir = taskData.workdir
            val convertedWorkdir = com.austinv11.nextflow.util.NextflowEnvironmentUtils.convertFromWslPathIfNeeded(workdir ?: "")
            if (convertedWorkdir.isNotEmpty()) {
                val logFile = File(convertedWorkdir, ".command.log")
                if (logFile.exists()) {
                    sb.append(logFile.readText()).append("\n\n")
                } else {
                    sb.append("No log file found at ${logFile.absolutePath}\n\n")
                }
            } else {
                sb.append("Remote execution or invalid workdir\n\n")
            }
        }
        logTextArea.text = sb.toString()
        logTextArea.caretPosition = 0
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
                    val duration = trace.get("realtime")?.asLong() ?: trace.get("duration")?.asLong()

                    val processNode = processNodes.getOrPut(processName) {
                        val node = DefaultMutableTreeNode(processName)
                        treeModel.insertNodeInto(node, treeRoot, treeRoot.childCount)
                        node
                    }

                    val taskId = trace.get("task_id")?.asText() ?: taskName
                    val taskData = NextflowTaskData(taskName, taskId, workdir, status, duration)

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
        stopLogTail()
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
    val status: String,
    val durationMs: Long? = null
) {
    override fun toString(): String {
        return "[$status] $name"
    }
}
