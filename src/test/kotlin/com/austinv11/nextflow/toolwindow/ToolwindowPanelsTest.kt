package com.austinv11.nextflow.toolwindow

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.application.ApplicationManager
import java.io.File
import java.nio.file.Files

class ToolwindowPanelsTest : BasePlatformTestCase() {

    fun testNextflowProjectPanelInstantiationAndContent() {
        val panel = NextflowProjectPanel(project)
        assertNotNull(panel)
    }

    fun testNextflowResourcesPanelInstantiationAndContent() {
        val panel = NextflowResourcesPanel(project)
        assertNotNull(panel)
    }

    fun testNextflowResourcesPanelBuildTree() {
        val panel = NextflowResourcesPanel(project)
        val method = NextflowResourcesPanel::class.java.getDeclaredMethod("buildTree", File::class.java, javax.swing.tree.DefaultMutableTreeNode::class.java)
        method.isAccessible = true
        
        // Use Java NIO to create a temporary directory with a valid path
        val dir = Files.createTempDirectory("nextflow-test-").toFile()

        try {
            File(dir, "main.nf").createNewFile()
            File(dir, "nextflow.config").createNewFile()
            File(dir, "ignore.txt").createNewFile()
            File(dir, ".hidden.nf").createNewFile()
            val workDir = File(dir, "work")
            workDir.mkdirs()
            File(workDir, "some.nf").createNewFile()

            val rootNode = javax.swing.tree.DefaultMutableTreeNode("Root")
            method.invoke(panel, dir, rootNode)

            val children = (0 until rootNode.childCount).map { rootNode.getChildAt(it) as javax.swing.tree.DefaultMutableTreeNode }
            val names = children.mapNotNull { (it.userObject as? File)?.name }

            assertTrue(names.contains("main.nf"))
            assertTrue(names.contains("nextflow.config"))
            assertFalse(names.contains("ignore.txt"))
            assertFalse(names.contains("work"))
            assertFalse(names.contains(".hidden.nf"))
        } finally {
            // Clean up the temporary directory
            dir.deleteRecursively()
        }
    }
}
