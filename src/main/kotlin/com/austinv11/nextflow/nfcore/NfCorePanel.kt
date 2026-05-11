package com.austinv11.nextflow.nfcore

import com.intellij.openapi.project.Project
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class NfCorePanel(private val project: Project) {
    private val treeModel: DefaultTreeModel
    private val tree: Tree

    init {
        val root = DefaultMutableTreeNode("nf-core Commands")

        // Pipelines
        val pipelinesNode = DefaultMutableTreeNode("Pipelines")
        pipelinesNode.add(DefaultMutableTreeNode(NfCoreAction("Create Pipeline", "create")))
        pipelinesNode.add(DefaultMutableTreeNode(NfCoreAction("Download Pipeline", "download")))
        pipelinesNode.add(DefaultMutableTreeNode(NfCoreAction("Lint Pipeline", "lint")))
        root.add(pipelinesNode)

        // Modules
        val modulesNode = DefaultMutableTreeNode("Modules")
        modulesNode.add(DefaultMutableTreeNode(NfCoreAction("Create Module", "modules create")))
        modulesNode.add(DefaultMutableTreeNode(NfCoreAction("Install Module", "modules install")))
        modulesNode.add(DefaultMutableTreeNode(NfCoreAction("Update Module", "modules update")))
        modulesNode.add(DefaultMutableTreeNode(NfCoreAction("Remove Module", "modules remove")))
        root.add(modulesNode)

        // Subworkflows
        val subworkflowsNode = DefaultMutableTreeNode("Subworkflows")
        subworkflowsNode.add(DefaultMutableTreeNode(NfCoreAction("Create Subworkflow", "subworkflows create")))
        subworkflowsNode.add(DefaultMutableTreeNode(NfCoreAction("Install Subworkflow", "subworkflows install")))
        subworkflowsNode.add(DefaultMutableTreeNode(NfCoreAction("Update Subworkflow", "subworkflows update")))
        subworkflowsNode.add(DefaultMutableTreeNode(NfCoreAction("Remove Subworkflow", "subworkflows remove")))
        root.add(subworkflowsNode)

        treeModel = DefaultTreeModel(root)
        tree = Tree(treeModel).apply {
            isRootVisible = false
            showsRootHandles = true
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION

            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        val path = getPathForLocation(e.x, e.y) ?: return
                        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                        val userObject = node.userObject
                        if (userObject is NfCoreAction) {
                            NfCoreRunner.executeAction(project, userObject)
                        }
                    }
                }
            })
        }

        // Expand all by default
        for (i in 0 until tree.rowCount) {
            tree.expandRow(i)
        }
    }

    fun getContent(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.add(ScrollPaneFactory.createScrollPane(tree), BorderLayout.CENTER)
        return panel
    }
}

data class NfCoreAction(val displayName: String, val commandType: String) {
    override fun toString(): String = displayName
}
