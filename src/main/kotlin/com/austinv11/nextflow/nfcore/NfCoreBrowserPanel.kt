package com.austinv11.nextflow.nfcore

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.io.HttpRequests
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class NfCoreBrowserPanel(private val project: Project) : Disposable {
    private val treeModel: DefaultTreeModel
    private val tree: Tree
    private val browserPanel: JPanel = JPanel(BorderLayout())
    private var browser: JBCefBrowser? = null

    init {
        val root = DefaultMutableTreeNode("Loading nf-core components...")
        treeModel = DefaultTreeModel(root)
        tree = Tree(treeModel).apply {
            isRootVisible = false
            showsRootHandles = true
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        }

        if (JBCefApp.isSupported()) {
            browser = JBCefBrowser()
            Disposer.register(this, browser!!)
            browserPanel.add(browser!!.component, BorderLayout.CENTER)
            browser!!.loadURL("https://nf-co.re/")
        } else {
            browserPanel.add(JLabel("JCEF (Chromium) is not supported in this environment.", SwingConstants.CENTER), BorderLayout.CENTER)
        }

        tree.addTreeSelectionListener(object : TreeSelectionListener {
            override fun valueChanged(e: TreeSelectionEvent) {
                val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
                val item = node.userObject as? NfCoreComponentItem ?: return
                browser?.loadURL(item.url)
            }
        })

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseReleased(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    val path = tree.getPathForLocation(e.x, e.y) ?: return
                    tree.selectionPath = path
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    val item = node.userObject as? NfCoreComponentItem ?: return

                    val popup = JPopupMenu()

                    if (item.type == NfCoreComponentType.PIPELINE) {
                        val pullItem = JMenuItem("Pull Pipeline")
                        pullItem.addActionListener {
                            val dialog = DownloadPipelineDialog(project, item.name)
                            if (dialog.showAndGet()) {
                                NfCoreRunner.executeCommandArgs(project, "Download Pipeline", dialog.getCommandArguments())
                            }
                        }
                        popup.add(pullItem)

                        val launchItem = JMenuItem("Launch Pipeline")
                        launchItem.addActionListener {
                            val dialog = LaunchPipelineDialog(project, item.name)
                            if (dialog.showAndGet()) {
                                NfCoreRunner.executeCommandArgs(project, "Launch Pipeline", dialog.getCommandArguments())
                            }
                        }
                        popup.add(launchItem)
                    } else if (item.type == NfCoreComponentType.MODULE || item.type == NfCoreComponentType.SUBWORKFLOW) {
                        val actionName = "Install ${item.type.displayName}"
                        val installItem = JMenuItem(actionName)
                        installItem.addActionListener {
                            val dialog = ManageItemDialog(project, "Install", item.type.pluralName, item.name)
                            if (dialog.showAndGet()) {
                                NfCoreRunner.executeCommandArgs(project, actionName, dialog.getCommandArguments())
                            }
                        }
                        popup.add(installItem)
                    }

                    if (popup.componentCount > 0) {
                        popup.show(tree, e.x, e.y)
                    }
                }
            }
        })

        loadData()
    }

    private fun loadData() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val pipelinesJsonStr = HttpRequests.request("https://nf-co.re/pipelines.json").readString()
                val componentsJsonStr = HttpRequests.request("https://nf-co.re/components.json").readString()

                val gson = Gson()
                val pipelinesJson = gson.fromJson(pipelinesJsonStr, JsonObject::class.java)
                val componentsJson = gson.fromJson(componentsJsonStr, JsonObject::class.java)

                data class PipelineEntry(val name: String, val stars: Int)
                val pipelinesList = pipelinesJson.getAsJsonArray("remote_workflows")
                    .map {
                        val obj = it.asJsonObject
                        PipelineEntry(
                            obj.get("name").asString,
                            obj.get("stargazers_count").asInt
                        )
                    }
                    .sortedByDescending { it.stars }

                val modulesList = componentsJson.getAsJsonArray("modules")
                    .map { it.asJsonObject.get("name").asString }
                    .sorted()

                val subworkflowsList = componentsJson.getAsJsonArray("subworkflows")
                    .map { it.asJsonObject.get("name").asString }
                    .sorted()

                ApplicationManager.getApplication().invokeLater {
                    val root = DefaultMutableTreeNode("nf-core Components")

                    val pipelinesNode = DefaultMutableTreeNode("Pipelines")
                    pipelinesList.forEach {
                        val displayText = "${it.name}  ★ ${it.stars}"
                        pipelinesNode.add(DefaultMutableTreeNode(NfCoreComponentItem(it.name, "https://nf-co.re/${it.name}", NfCoreComponentType.PIPELINE, displayText)))
                    }
                    root.add(pipelinesNode)

                    val modulesNode = DefaultMutableTreeNode("Modules")
                    modulesList.forEach { modulesNode.add(DefaultMutableTreeNode(NfCoreComponentItem(it, "https://nf-co.re/modules/$it", NfCoreComponentType.MODULE))) }
                    root.add(modulesNode)

                    val subworkflowsNode = DefaultMutableTreeNode("Subworkflows")
                    subworkflowsList.forEach { subworkflowsNode.add(DefaultMutableTreeNode(NfCoreComponentItem(it, "https://nf-co.re/subworkflows/$it", NfCoreComponentType.SUBWORKFLOW))) }
                    root.add(subworkflowsNode)

                    treeModel.setRoot(root)
                    tree.isRootVisible = false
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    val root = DefaultMutableTreeNode("Failed to load components.")
                    root.add(DefaultMutableTreeNode(e.message))
                    treeModel.setRoot(root)
                    tree.isRootVisible = true
                }
            }
        }
    }

    fun getContent(): JComponent {
        val splitter = JBSplitter(false, 0.3f)
        splitter.firstComponent = ScrollPaneFactory.createScrollPane(tree)
        splitter.secondComponent = browserPanel
        return splitter
    }

    override fun dispose() {
    }
}

enum class NfCoreComponentType(val displayName: String, val pluralName: String) {
    PIPELINE("Pipeline", "Pipelines"),
    MODULE("Module", "Modules"),
    SUBWORKFLOW("Subworkflow", "Subworkflows")
}

data class NfCoreComponentItem(
    val name: String,
    val url: String,
    val type: NfCoreComponentType,
    val customDisplay: String? = null
) {
    override fun toString(): String = customDisplay ?: name
}
