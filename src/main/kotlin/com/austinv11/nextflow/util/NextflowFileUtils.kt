package com.austinv11.nextflow.util

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
