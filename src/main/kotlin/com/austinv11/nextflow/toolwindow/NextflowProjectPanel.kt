package com.austinv11.nextflow.toolwindow

import com.google.gson.internal.LinkedTreeMap
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.austinv11.nextflow.lsp.NextflowLspServerSupportProvider
import org.eclipse.lsp4j.ExecuteCommandParams
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.JTree

class NextflowProjectPanel(private val project: Project) {
    private val logger = Logger.getInstance(NextflowProjectPanel::class.java)
    private val treeModel: DefaultTreeModel
    private val tree: Tree

    init {
        val root = DefaultMutableTreeNode("Workspace")
        treeModel = DefaultTreeModel(root)
        tree = Tree(treeModel).apply {
            isRootVisible = false
            showsRootHandles = true
            cellRenderer = ProjectTreeCellRenderer()
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        val path = getPathForLocation(e.x, e.y) ?: return
                        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val userObject = node.userObject
                        if (userObject is ProjectNodeInfo) {
                            openFile(userObject.path, userObject.line)
                        }
                    }
                }
            })
        }
    }

    fun getContent(): JComponent {
        val panel = JPanel(BorderLayout())

        val actionGroup = DefaultActionGroup().apply {
            add(object : AnAction("Refresh", "Refresh project view", AllIcons.Actions.Refresh) {
                override fun actionPerformed(e: AnActionEvent) {
                    refreshData()
                }
            })
        }
        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar("NextflowProjectView", actionGroup, true)
        actionToolbar.targetComponent = panel

        panel.add(actionToolbar.component, BorderLayout.NORTH)
        panel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER)

        ApplicationManager.getApplication().invokeLater {
            refreshData()
        }

        return panel
    }

    private fun openFile(filePath: String, line: Int) {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return
        val descriptor = OpenFileDescriptor(project, virtualFile, maxOf(0, line), 0)
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
    }

    private fun refreshData() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val server = LspServerManager.getInstance(project)
                .getServersForProvider(NextflowLspServerSupportProvider::class.java)
                .firstOrNull { it.state == LspServerState.Running }

            if (server == null) {
                logger.warn("No running Nextflow LSP server found")
                return@executeOnPooledThread
            }

            try {
                val result = server.sendRequestSync { languageServer ->
                    languageServer.workspaceService.executeCommand(
                        ExecuteCommandParams().apply {
                            this.command = "nextflow.server.previewWorkspace"
                            this.arguments = listOf(project.name)
                        }
                    )
                }

                val testNodes = findNfTests(project.basePath)
                val testMap = mutableMapOf<String, MutableList<TestNodeInfo>>()
                for (test in testNodes) {
                    val dir = File(test.path).parent
                    testMap.getOrPut(dir) { mutableListOf() }.add(test)
                }

                val nodes = parseLspResult(result)
                val nodeByKey = nodes.associateBy { it.key() }
                val childKeys = nodes.flatMap { it.children }.map { it.key() }.toSet()

                val root = DefaultMutableTreeNode("Workspace")
                for (node in nodes.filterNot { childKeys.contains(it.key()) }) {
                    root.add(buildTreeNode(node, nodeByKey, testMap, mutableSetOf()))
                }

                ApplicationManager.getApplication().invokeLater {
                    treeModel.setRoot(root)
                    treeModel.reload()
                    expandAll(tree, 0, tree.rowCount)
                }
            } catch (e: Exception) {
                logger.error("Failed to fetch project view data", e)
            }
        }
    }

    private fun expandAll(tree: JTree, startRow: Int, endRow: Int) {
        var row = startRow
        while (row < endRow) {
            tree.expandRow(row)
            row += 1
        }
        if (tree.rowCount > endRow) {
            expandAll(tree, endRow, tree.rowCount)
        }
    }

    private fun buildTreeNode(
        node: ProjectNodeInfo,
        nodeByKey: Map<String, ProjectNodeInfo>,
        testMap: Map<String, List<TestNodeInfo>>,
        ancestorKeys: MutableSet<String>
    ): DefaultMutableTreeNode {
        val treeNode = DefaultMutableTreeNode(node)
        val key = node.key()
        if (!ancestorKeys.add(key)) return treeNode

        val nodeDir = File(node.path).parent
        val matchingTest = testMap[nodeDir]?.find { it.name == node.name }
        if (matchingTest != null) {
            treeNode.add(
                DefaultMutableTreeNode(
                    ProjectNodeInfo(matchingTest.name, "test", matchingTest.path, matchingTest.line)
                )
            )
        }

        for (child in node.children) {
            val resolvedChild = nodeByKey[child.key()]
                ?: ProjectNodeInfo(child.name, "unknown", child.path, 0)
            treeNode.add(buildTreeNode(resolvedChild, nodeByKey, testMap, ancestorKeys))
        }

        ancestorKeys.remove(key)
        return treeNode
    }

    private fun parseLspResult(result: Any?): List<ProjectNodeInfo> {
        val nodes = mutableListOf<ProjectNodeInfo>()
        if (result is LinkedTreeMap<*, *>) {
            val resultArray = result["result"] as? List<*> ?: return nodes
            for (item in resultArray) {
                if (item is LinkedTreeMap<*, *>) {
                    val name = item["name"] as? String ?: continue
                    val type = item["type"] as? String ?: continue
                    val path = item["path"] as? String ?: continue
                    val line = (item["line"] as? Double)?.toInt() ?: 0
                    val children = parseChildren(item["children"])
                    nodes.add(ProjectNodeInfo(name, type, path, line, children))
                }
            }
        }
        return nodes
    }

    private fun parseChildren(children: Any?): List<ProjectNodeRef> {
        val refs = mutableListOf<ProjectNodeRef>()
        val childrenArray = children as? List<*> ?: return refs
        for (child in childrenArray) {
            if (child is LinkedTreeMap<*, *>) {
                val name = child["name"] as? String ?: continue
                val path = child["path"] as? String ?: continue
                refs.add(ProjectNodeRef(name, path))
            }
        }
        return refs
    }

    private fun findNfTests(dirPath: String?): List<TestNodeInfo> {
        if (dirPath == null) return emptyList()
        val results = mutableListOf<TestNodeInfo>()
        val dir = File(dirPath)
        if (!dir.exists() || !dir.isDirectory) return results

        dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".nf.test") }
            .forEach { file ->
                val text = file.readText()
                NF_TEST_REGEX.findAll(text).forEach { matchResult ->
                    val name = matchResult.groupValues[2]
                    val index = matchResult.range.first
                    val line = text.substring(0, index).count { it == '\n' }
                    results.add(TestNodeInfo(name, file.absolutePath, line))
                }
            }
        return results
    }

    companion object {
        private val NF_TEST_REGEX = Regex("""^\s*(process|workflow)\s+\"(\w+)\"""", setOf(RegexOption.MULTILINE))
    }

    private class ProjectTreeCellRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree, value: Any?, sel: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean
        ): java.awt.Component {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            if (!sel) background = tree.background
            if (value is DefaultMutableTreeNode) {
                val userObject = value.userObject
                if (userObject is ProjectNodeInfo) {
                    text = if (userObject.name == "<entry>") {
                        "Entry (${File(userObject.path).name})"
                    } else {
                        userObject.name
                    }
                    icon = when (userObject.type.lowercase()) {
                        "workflow" -> AllIcons.Nodes.Deploy
                        "process" -> AllIcons.Gutter.WriteAccess
                        "test" -> AllIcons.Gutter.ReadAccess
                        else -> AllIcons.Debugger.Question_badge
                    }
                }
            }
            return this
        }
    }

    data class ProjectNodeInfo(
        val name: String,
        val type: String,
        val path: String,
        val line: Int,
        val children: List<ProjectNodeRef> = emptyList()
    ) {
        fun key(): String = "$path::$name"
        override fun toString() = name
    }

    data class ProjectNodeRef(val name: String, val path: String) {
        fun key(): String = "$path::$name"
    }

    data class TestNodeInfo(val name: String, val path: String, val line: Int)
}