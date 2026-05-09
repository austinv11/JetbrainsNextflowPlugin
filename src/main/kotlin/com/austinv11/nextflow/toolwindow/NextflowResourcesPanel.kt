package com.austinv11.nextflow.toolwindow

import com.austinv11.nextflow.util.NextflowFileUtils
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.austinv11.nextflow.NextflowIcons
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class NextflowResourcesPanel(private val project: Project) {
    private val treeModel: DefaultTreeModel
    private val tree: Tree

    init {
        val root = DefaultMutableTreeNode("Resources")
        treeModel = DefaultTreeModel(root)
        tree = Tree(treeModel).apply {
            isRootVisible = false
            showsRootHandles = true
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            cellRenderer = ResourceTreeCellRenderer()
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        val path = getPathForLocation(e.x, e.y) ?: return
                        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val file = node.userObject as? File ?: return
                        if (file.isFile) {
                            openFile(file.absolutePath)
                        }
                    }
                }
            })
        }
    }

    fun getContent(): JComponent {
        val panel = JPanel(BorderLayout())

        val actionGroup = DefaultActionGroup().apply {
            add(object : AnAction("Refresh", "Refresh resources view", AllIcons.Actions.Refresh) {
                override fun actionPerformed(e: AnActionEvent) {
                    refreshData()
                }
            })
        }
        val actionToolbar = ActionManager.getInstance()
            .createActionToolbar("NextflowResourcesView", actionGroup, true)
        actionToolbar.targetComponent = panel

        panel.add(actionToolbar.component, BorderLayout.NORTH)
        panel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER)

        ApplicationManager.getApplication().invokeLater {
            refreshData()
        }

        return panel
    }

    private fun openFile(filePath: String) {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return
        val descriptor = OpenFileDescriptor(project, virtualFile)
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
    }

    private fun refreshData() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val basePath = project.basePath ?: return@executeOnPooledThread
            val rootDir = File(basePath)

            val newRoot = DefaultMutableTreeNode("Resources")
            buildTree(rootDir, newRoot)

            ApplicationManager.getApplication().invokeLater {
                treeModel.setRoot(newRoot)
                treeModel.reload()
            }
        }
    }

    private fun buildTree(dir: File, node: DefaultMutableTreeNode) {
        val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: return
        for (file in files) {
            // Ignore hidden files and work/ directories
            if (file.name.startsWith(".") && file.name != ".nf.test") continue
            if (file.isDirectory && file.name == "work") continue

            if (file.isDirectory) {
                val dirNode = DefaultMutableTreeNode(file)
                buildTree(file, dirNode)
                if (dirNode.childCount > 0) {
                    node.add(dirNode)
                }
            } else if (NextflowFileUtils.isNextflowFile(file.name)) {
                node.add(DefaultMutableTreeNode(file))
            }
        }
    }

    private class ResourceTreeCellRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree, value: Any?, sel: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean
        ): Component {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            if (!sel) background = tree.background
            if (value is DefaultMutableTreeNode) {
                val file = value.userObject as? File
                if (file != null) {
                    text = file.name
                    icon = if (file.isDirectory) {
                        AllIcons.Nodes.Folder
                    } else if (NextflowFileUtils.isNextflowTest(file.name)) {
                        AllIcons.RunConfigurations.TestState.Run
                    } else {
                        NextflowIcons.FILE
                    }
                }
            }
            return this
        }
    }
}