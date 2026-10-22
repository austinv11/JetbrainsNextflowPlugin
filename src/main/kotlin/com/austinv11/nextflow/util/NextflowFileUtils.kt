package com.austinv11.nextflow.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile

object NextflowFileUtils {
    const val NEXTFLOW_EXTENSION = "nf"
    const val NEXTFLOW_TEST_EXTENSION = "nf.test"
    const val NEXTFLOW_CONFIG_NAME = "nextflow.config"

    fun isNextflowScript(file: VirtualFile): Boolean {
        return file.extension == NEXTFLOW_EXTENSION
    }

    fun isNextflowScript(fileName: String): Boolean {
        return fileName.endsWith(".$NEXTFLOW_EXTENSION")
    }

    fun isNextflowConfig(file: VirtualFile): Boolean {
        return file.name == NEXTFLOW_CONFIG_NAME
    }

    fun isNextflowConfig(fileName: String): Boolean {
        return fileName == NEXTFLOW_CONFIG_NAME
    }

    /** Additional configs require a pipeline marker within the same project content root. */
    fun isNextflowConfig(project: Project, file: VirtualFile): Boolean {
        if (file.isDirectory) return false
        if (isNextflowConfig(file)) return true
        if (file.extension != "config" || file.name == "nf-test.config") return false
        val root = ProjectFileIndex.getInstance(project).getContentRootForFile(file) ?: return false
        var directory = file.parent
        while (directory != null) {
            if (directory.findChild(NEXTFLOW_CONFIG_NAME)?.isDirectory == false) return true
            if (directory == root) break
            directory = directory.parent
        }
        return false
    }

    fun isNextflowLspFile(project: Project, file: VirtualFile): Boolean =
        !file.isDirectory && (isNextflowScript(file) || isNextflowConfig(project, file))

    fun isNextflowTest(file: VirtualFile): Boolean {
        return file.name.endsWith(".$NEXTFLOW_TEST_EXTENSION")
    }

    fun isNextflowTest(fileName: String): Boolean {
        return fileName.endsWith(".$NEXTFLOW_TEST_EXTENSION")
    }

    fun isNextflowFile(file: VirtualFile): Boolean {
        return isNextflowScript(file) || isNextflowConfig(file) || isNextflowTest(file)
    }

    fun isNextflowFile(fileName: String): Boolean {
        return isNextflowScript(fileName) || isNextflowConfig(fileName) || isNextflowTest(fileName)
    }
}
